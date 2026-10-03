package com.znewk.kcd.client.host;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import com.znewk.kcd.KcdMod;

/**
 * Хост прямо из игры: после загрузки мира открывает его для сети (без проверки Mojang — у всех TLauncher)
 * и поднимает мост playit, чтобы друзья заходили из интернета по постоянному адресу.
 */
public final class HostSession {
    /** Порт, на который смотрит туннель playit (стандартный для Minecraft). */
    public static final int PORT = 25565;

    /** Для автотестов: -Dkcd.autohost=true открывает для отряда первый загруженный мир. */
    private static boolean autoHost = Boolean.getBoolean("kcd.autohost");
    private static boolean pendingHost;
    private static boolean hosting;

    private HostSession() {}

    /** Следующий загруженный одиночный мир откроется для отряда. */
    public static void requestHost() {
        pendingHost = true;
    }

    public static void cancelRequest() {
        pendingHost = false;
    }

    public static boolean isHosting() {
        return hosting;
    }

    public static void clientTick() {
        if (!pendingHost && !autoHost) return;
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        pendingHost = false;
        autoHost = false;
        if (server.isPublished()) return;

        server.setUsesAuthentication(false);
        if (server.publishServer(null, false, PORT)) {
            hosting = true;
            KcdMod.LOGGER.info("KCD: мир открыт для отряда на порту {}", PORT);
            chat(Component.translatable("kcd.host.opened").withStyle(ChatFormatting.GOLD));
            PlayitService.start();
        } else {
            chat(Component.translatable("kcd.host.port_busy", PORT).withStyle(ChatFormatting.RED));
        }
    }

    public static void onLogout() {
        pendingHost = false;
        if (hosting) {
            hosting = false;
            PlayitService.stop();
        }
    }

    // ------------------------------------------------------------------ сообщения от PlayitService (из фонового потока)

    static void notifyClaim(String url) {
        Minecraft.getInstance().execute(() -> {
            MutableComponent link = Component.translatable("kcd.host.claim.link").withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url))));
            chat(Component.translatable("kcd.host.claim", link).withStyle(ChatFormatting.YELLOW));
            Util.getPlatform().openUri(url);
        });
    }

    static void notifyAddress(String address) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(address);
            chat(Component.translatable("kcd.host.ready", addressComponent(address)).withStyle(ChatFormatting.GREEN));
        });
    }

    static void notifyStatus() {
        PlayitService.State s = PlayitService.state();
        Minecraft.getInstance().execute(() -> {
            switch (s) {
                case DOWNLOADING -> chat(Component.translatable("kcd.host.downloading").withStyle(ChatFormatting.GRAY));
                case CONNECTING -> chat(Component.translatable("kcd.host.connecting").withStyle(ChatFormatting.GRAY));
                case ERROR -> chat(Component.translatable("kcd.host.error", String.valueOf(PlayitService.error())).withStyle(ChatFormatting.RED));
                default -> {}
            }
        });
    }

    /** Адрес, по клику копируется в буфер обмена. */
    public static MutableComponent addressComponent(String address) {
        return Component.literal(address).withStyle(Style.EMPTY
            .withColor(ChatFormatting.WHITE).withBold(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, address))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("kcd.host.copy"))));
    }

    private static void chat(Component msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.getChat().addMessage(msg);
    }
}
