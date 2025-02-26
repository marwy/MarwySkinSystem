package marwy.marwyskinsystem.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.util.helper.GetSkinUrlThread;
import net.minecraft.core.entity.player.Player;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

@Mixin(value = GetSkinUrlThread.class, remap = false)
public class GetSkinUrlThreadMixin {

    @Shadow(remap = false) private Player player;
    @Shadow(remap = false) private UUID uuid;
    @Shadow(remap = false) private static Logger LOGGER;

    // URL для текстур
    private static final String ELY_BY_TEXTURES_URL = "https://skinsystem.ely.by/textures/";
    private static final String MARWY_CAPE_URL = "https://api.milkis.fun/cloaks/";

    @Inject(method = "run()V", at = @At("HEAD"), remap = false, cancellable = true)
    private void tryGetSkinFromElyByEarly(CallbackInfo ci) {
        String name = player.username;
        if (name != null && !name.isEmpty()) {
            LOGGER.info("Попытка получить текстуры для {}", name);

            boolean skinFound = false;
            boolean capeFound = false;
            
            // Сначала проверяем плащ на Marwy API (используется только для плащей)
            String marwyCapeUrl = MARWY_CAPE_URL + name + ".png";
            boolean marwyCapeFound = checkMarwyCape(marwyCapeUrl);
            
            // Затем проверяем Ely.by для скина и модели
            boolean elyByTexturesFound = getElyByTextures(name);
            
            // Если нашли текстуры на одном из серверов, отменяем стандартный метод
            if (marwyCapeFound || elyByTexturesFound) {
                ci.cancel();
            } else {
                LOGGER.info("Текстуры не найдены ни на одном из серверов, использую стандартный метод");
            }
        }
    }

    /**
     * Проверяет наличие плаща на Marwy API
     * @param capeUrl URL плаща
     * @return true, если плащ найден
     */
    private boolean checkMarwyCape(String capeUrl) {
        try {
            URL url = new URL(capeUrl);
            LOGGER.info("Проверка плаща Marwy capes api: {}", capeUrl);
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            
            int responseCode = connection.getResponseCode();
            LOGGER.info("Код ответа Marwy API: {}", responseCode);
            
            if (responseCode == 200) {
                player.capeURL = capeUrl;
                LOGGER.info("Получен плащ с Marwy API: {}", capeUrl);
                return true;
            } else {
                LOGGER.info("Плащ на Marwy API не найден");
                return false;
            }
        } catch (Exception e) {
            LOGGER.warn("Ошибка при проверке плаща на Marwy API: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Получает текстуры с Ely.by API
     * @param username Имя игрока
     * @return true, если текстуры найдены
     */
    private boolean getElyByTextures(String username) {
        try {
            URL url = new URL(ELY_BY_TEXTURES_URL + username);
            LOGGER.info("Проверка текстур на Ely.by: {}", url);
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            LOGGER.info("Код ответа Ely.by API: {}", responseCode);
            
            if (responseCode == 204) {
                LOGGER.info("Текстуры не найдены на Ely.by");
                return false;
            }
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                String jsonResponse = response.toString();
                LOGGER.info("Получен ответ от Ely.by: {}", jsonResponse);
                
                // Разбор JSON
                JsonObject texturesJson = new JsonParser().parse(jsonResponse).getAsJsonObject();
                boolean foundAny = false;
                
                // Получение URL скина и информации о модели
                if (texturesJson.has("SKIN")) {
                    JsonObject skinData = texturesJson.getAsJsonObject("SKIN");
                    if (skinData.has("url")) {
                        player.skinURL = skinData.get("url").getAsString();
                        LOGGER.info("Установлен URL скина: {}", player.skinURL);
                        
                        // Проверка модели скина
                        if (skinData.has("metadata") && skinData.getAsJsonObject("metadata").has("model")) {
                            String model = skinData.getAsJsonObject("metadata").get("model").getAsString();
                            if ("slim".equals(model)) {
                                player.slimModel = true;
                                LOGGER.info("Установлена slim модель скина");
                            } else {
                                player.slimModel = false;
                                LOGGER.info("Установлена обычная модель скина");
                            }
                        } else {
                            player.slimModel = false;
                            LOGGER.info("Модель не указана, используется стандартная");
                        }
                        foundAny = true;
                    }
                }
                
                // Получение URL плаща, только если не нашли на Marwy API
                if (player.capeURL == null && texturesJson.has("CAPE")) {
                    JsonObject capeData = texturesJson.getAsJsonObject("CAPE");
                    if (capeData.has("url")) {
                        player.capeURL = capeData.get("url").getAsString();
                        LOGGER.info("Установлен URL плаща с Ely.by: {}", player.capeURL);
                        foundAny = true;
                    }
                }
                
                return foundAny;
            }
            
            return false;
        } catch (Exception e) {
            LOGGER.warn("Ошибка при получении текстур с Ely.by: {}", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
