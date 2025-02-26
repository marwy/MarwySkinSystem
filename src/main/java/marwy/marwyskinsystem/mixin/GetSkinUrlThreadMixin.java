package marwy.marwyskinsystem.mixin;

import net.minecraft.core.util.helper.GetSkinUrlThread;
import net.minecraft.core.entity.player.Player;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

@Mixin(value = GetSkinUrlThread.class, remap = false)
public class GetSkinUrlThreadMixin {

    @Shadow(remap = false) private Player player;
    @Shadow(remap = false) private UUID uuid;
    @Shadow(remap = false) private static Logger LOGGER;

    // Исправленные URL для ely.by
    private static final String ELY_BY_SKIN_URL = "https://skinsystem.ely.by/skins/";
    private static final String ELY_BY_CAPE_URL = "https://skinsystem.ely.by/cloaks/";
    private static final String MARWY_CAPE_URL = "https://api.milkis.fun/cloaks/";

    @Inject(method = "run()V", at = @At("HEAD"), remap = false, cancellable = true)
    private void tryGetSkinFromElyByEarly(CallbackInfo ci) {
        String name = player.username;
        if (name != null && !name.isEmpty()) {
            LOGGER.info("Попытка получить скин с ely.by для {}", name);

            try {
                // Проверяем доступность скина на ely.by
                String skinCheckUrl = ELY_BY_SKIN_URL + name + ".png";
                String marwyCapeCheckUrl = MARWY_CAPE_URL + name + ".png";
                String elyByCapeCheckUrl = ELY_BY_CAPE_URL + name + ".png";

                LOGGER.info("Проверка скина по URL: {}", skinCheckUrl);
                LOGGER.info("Проверка плаща по URL (marwy capes): {}", marwyCapeCheckUrl);
                
                String skinCheckResult = checkResourceExists(skinCheckUrl);
                String marwyCapeCheckResult = checkResourceExists(marwyCapeCheckUrl);
                
                boolean capeFound = false;
                
                if (skinCheckResult != null) {
                    if (skinCheckResult.equals("OK")) {
                        player.skinURL = skinCheckUrl;
                        LOGGER.info("Получен скин с ely.by: {}", player.skinURL);
                    } else {
                        player.skinURL = skinCheckResult;
                        LOGGER.info("Получен скин с ely.by (перенаправление): {}", player.skinURL);
                    }
                    
                    // Проверяем сначала плащ на api.milkis.fun
                    if (marwyCapeCheckResult != null && marwyCapeCheckResult.equals("OK")) {
                        player.capeURL = marwyCapeCheckUrl;
                        LOGGER.info("Получен плащ с marwy capes: {}", player.capeURL);
                        capeFound = true;
                    } else {
                        LOGGER.info("Плащ marwy capes не найден, проверяем ely.by");
                        // Если не найден на api.milkis.fun, проверяем на ely.by
                        String elyByCapeCheckResult = checkResourceExists(elyByCapeCheckUrl);
                        if (elyByCapeCheckResult != null && elyByCapeCheckResult.equals("OK")) {
                            player.capeURL = elyByCapeCheckUrl;
                            LOGGER.info("Получен плащ с ely.by: {}", player.capeURL);
                            capeFound = true;
                        }
                    }
                    
                    ci.cancel(); // Отменяем оригинальный метод run(), если скин с ely.by получен
                } else {
                    LOGGER.info("Скин ely.by не найден");
                    // Не отменяем, пусть выполняется оригинальный run()
                }

            } catch (Exception e) {
                LOGGER.warn("Ошибка при получении скина/плаща", e);
                // Не отменяем, пусть выполняется оригинальный run()
            }
        }
    }

    private String checkResourceExists(String urlString) {
        try {
            URL url = new URL(urlString);
            LOGGER.info("Попытка подключения к: {}", urlString);
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            // Увеличиваем таймауты
            connection.setInstanceFollowRedirects(false);
            HttpURLConnection.setFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setConnectTimeout(10000);  // Увеличиваем до 10 секунд
            connection.setReadTimeout(10000);     // Увеличиваем до 10 секунд
            
            // Более подробное логирование
            try {
                int responseCode = connection.getResponseCode();
                LOGGER.info("Проверка ресурса {}: код ответа {}", urlString, responseCode);

                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM) { // 301
                    String redirectedUrl = connection.getHeaderField("Location");
                    LOGGER.info("Перенаправление на URL: {}", redirectedUrl);
                    return redirectedUrl; // Возвращаем URL перенаправления
                } else if (responseCode == HttpURLConnection.HTTP_OK) { // 200
                    return "OK"; // Успех
                } else {
                    LOGGER.warn("Неожиданный код ответа {} для {}", responseCode, urlString);
                    return null;
                }
            } catch (Exception e) {
                LOGGER.warn("Ошибка при получении кода ответа от {}: {}", urlString, e.getMessage());
                return null;
            }
        } catch (java.net.UnknownHostException e) {
            LOGGER.warn("Ошибка DNS для домена {}: {}", urlString, e.getMessage());
            return null;
        } catch (java.net.SocketTimeoutException e) {
            LOGGER.warn("Таймаут подключения к {}: {}", urlString, e.getMessage());
            return null;
        } catch (javax.net.ssl.SSLHandshakeException e) {
            LOGGER.warn("Ошибка SSL для {}: {}", urlString, e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("Ошибка при проверке ресурса {}: {}", urlString, e.getMessage());
            e.printStackTrace(); // Более подробный стектрейс для отладки
            return null;
        }
    }
}
