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

    @Inject(method = "run()V", at = @At("HEAD"), remap = false, cancellable = true)
    private void tryGetSkinFromElyByEarly(CallbackInfo ci) {
        String name = player.username;
        if (name != null && !name.isEmpty()) {
            LOGGER.info("Попытка получить скин с ely.by в начале run() для {}", name);

            try {
                // Проверяем доступность скина на ely.by
                String skinCheckUrl = ELY_BY_SKIN_URL + name + ".png";
                String capeCheckUrl = ELY_BY_CAPE_URL + name + ".png";

                LOGGER.info("Проверка скина по URL: {}", skinCheckUrl);
                String skinCheckResult = checkResourceExists(skinCheckUrl);
                String capeCheckResult = checkResourceExists(capeCheckUrl);

                if (skinCheckResult != null) {
                    if (skinCheckResult.equals("OK")) {
                        player.skinURL = skinCheckUrl;
                        LOGGER.info("Получен скин с ely.by (рано): {}", player.skinURL);
                    } else {
                        player.skinURL = skinCheckResult;
                        LOGGER.info("Получен скин с ely.by (перенаправление, рано): {}", player.skinURL);
                    }
                    if (capeCheckResult != null && capeCheckResult.equals("OK")) {
                        player.capeURL = capeCheckUrl;
                        LOGGER.info("Получен плащ с ely.by (рано): {}", player.capeURL);
                    }
                    ci.cancel(); // Отменяем оригинальный метод run(), если скин с ely.by получен
                } else {
                    LOGGER.info("Скин на ely.by не найден (рано), продолжаем стандартную загрузку");
                    // Не отменяем, пусть выполняется оригинальный run()
                }

            } catch (Exception e) {
                LOGGER.warn("Ошибка при получении скина с ely.by (рано)", e);
                // Не отменяем, пусть выполняется оригинальный run()
            }
        }
    }

    private String checkResourceExists(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            // Добавляем User-Agent для избежания блокировки
            connection.setInstanceFollowRedirects(false);
            HttpURLConnection.setFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            LOGGER.info("Проверка ресурса {}: код ответа {}", urlString, responseCode);

            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM) { // 301
                String redirectedUrl = connection.getHeaderField("Location");
                LOGGER.info("Перенаправление на URL: {}", redirectedUrl);
                return redirectedUrl; // Возвращаем URL перенаправления
            } else if (responseCode == HttpURLConnection.HTTP_OK) { // 200
                return "OK"; // Возвращаем "OK", чтобы обозначить успех (можно заменить на true)
            } else {
                return null; // Возвращаем null в случае ошибки или иного кода ответа
            }

        } catch (Exception e) {
            LOGGER.warn("Ошибка при проверке ресурса {}: {}", urlString, e.getMessage());
            return null;
        }
    }
}
