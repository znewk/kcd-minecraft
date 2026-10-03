import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Превью: как панорама выглядит в игре (перспектива как в CubeMap) + логотип и меню поверх.
 * java tools/art/Preview.java <папка assets> <выход.png> <yaw>
 */
public class Preview {
    public static void main(String[] args) throws Exception {
        String assets = args[0];
        BufferedImage[] f = new BufferedImage[6];
        for (int i = 0; i < 6; i++) f[i] = ImageIO.read(new File(assets, "minecraft/textures/gui/title/background/panorama_" + i + ".png"));
        int W = 1280, H = 720;
        double yaw = Math.toRadians(Double.parseDouble(args[2])), pitch = Math.toRadians(10);
        double fovY = 1.4835298, t = Math.tan(fovY / 2), aspect = W / (double) H;
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            // направление в пространстве камеры (смотрим в -z, y вверх)
            double cx = (2 * (x + 0.5) / W - 1) * t * aspect, cy = (1 - 2 * (y + 0.5) / H) * t, cz = -1;
            // наклон вниз (pitch) и поворот (yaw)
            double y1 = cy * Math.cos(pitch) + cz * Math.sin(pitch), z1 = -cy * Math.sin(pitch) + cz * Math.cos(pitch);
            double x2 = cx * Math.cos(yaw) + z1 * Math.sin(yaw), z2 = -cx * Math.sin(yaw) + z1 * Math.cos(yaw);
            // мир -> координаты куба до поворота CubeMap (x, -y, -z)
            double px = x2, py = -y1, pz = -z2;
            out.setRGB(x, y, sample(f, px, py, pz));
        }
        Graphics2D g = out.createGraphics();
        // ванильный overlay
        BufferedImage ov = ImageIO.read(new File(assets, "minecraft/textures/gui/title/background/panorama_overlay.png"));
        g.drawImage(ov, 0, 0, W, H, null);
        BufferedImage shade = ImageIO.read(new File(assets, "kcd/textures/gui/title/menu_shade.png"));
        g.drawImage(shade, W / 2, 0, W / 2, H, null);
        BufferedImage logo = ImageIO.read(new File(assets, "kcd/textures/gui/title/logo.png"));
        int lw = (int) (W * 0.42), lh = logo.getHeight() * lw / logo.getWidth();
        g.drawImage(logo, W - lw - 50, 40, lw, lh, null);
        BufferedImage hl = ImageIO.read(new File(assets, "kcd/textures/gui/sprites/menu/highlight.png"));
        String[] items = {"Играть с отрядом", "Одиночная игра", "Настройки", "Моды", "", "Выход"};
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        int my = 300, cxm = W - 50 - lw / 2;
        for (int i = 0; i < items.length; i++) {
            if (items[i].isEmpty()) { my += 24; continue; }
            int tw = g.getFontMetrics().stringWidth(items[i]);
            if (i == 2) {
                int bw = tw + 60, bh = 36;
                g.drawImage(hl, cxm - bw / 2, my - 26, cxm - bw / 2 + 24, my - 26 + bh, 0, 0, 8, 16, null);
                g.drawImage(hl, cxm - bw / 2 + 24, my - 26, cxm + bw / 2 - 24, my - 26 + bh, 8, 0, 56, 16, null);
                g.drawImage(hl, cxm + bw / 2 - 24, my - 26, cxm + bw / 2, my - 26 + bh, 56, 0, 64, 16, null);
                g.setColor(new Color(0x2A1A0A));
            } else {
                g.setColor(new Color(0x3F3F3F)); g.drawString(items[i], cxm - tw / 2 + 2, my + 2);
                g.setColor(new Color(0xF2E8D5));
            }
            g.drawString(items[i], cxm - tw / 2, my);
            my += 44;
        }
        g.dispose();
        ImageIO.write(out, "png", new File(args[1]));
    }

    static int sample(BufferedImage[] f, double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        int k; double u, v;
        if (az >= ax && az >= ay) {
            if (z > 0) { k = 0; u = (x / az + 1) / 2; v = (y / az + 1) / 2; }
            else { k = 2; u = (1 - x / az) / 2; v = (y / az + 1) / 2; }
        } else if (ax >= ay) {
            if (x > 0) { k = 1; u = (1 - z / ax) / 2; v = (y / ax + 1) / 2; }
            else { k = 3; u = (z / ax + 1) / 2; v = (y / ax + 1) / 2; }
        } else {
            if (y < 0) { k = 4; u = (x / ay + 1) / 2; v = (z / ay + 1) / 2; }
            else { k = 5; u = (x / ay + 1) / 2; v = (1 - z / ay) / 2; }
        }
        BufferedImage im = f[k];
        int px = Math.min(im.getWidth() - 1, Math.max(0, (int) (u * im.getWidth())));
        int py = Math.min(im.getHeight() - 1, Math.max(0, (int) (v * im.getHeight())));
        return im.getRGB(px, py);
    }
}
