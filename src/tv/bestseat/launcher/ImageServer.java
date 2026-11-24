package tv.bestseat.launcher;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Kleine Webseite im WLAN, um ein Kachelbild vom Handy oder Rechner zu schicken (W126).
 * Läuft nur, solange das Fenster „Change Image“ offen ist; die Adresse enthält ein zufälliges
 * Kürzel. Die Seite kann eine Bild-URL schicken (der TV lädt sie) oder eine Bilddatei hochladen.
 */
final class ImageServer {

    interface Listener {
        /** Auf dem Haupt-Thread: Bild gespeichert. */
        void onSaved();
    }

    private static final String TAG = "Launcher.ImageServer";
    private static final int PORT = 8765;
    private static final int MAX_BYTES = 15 * 1024 * 1024;

    private final Context context;
    private final String pkg;
    private final String label;
    private final Listener listener;
    private final String token;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ServerSocket server;
    private volatile boolean running;

    ImageServer(Context context, String pkg, String label, Listener listener) {
        this.context = context.getApplicationContext();
        this.pkg = pkg;
        this.label = label;
        this.listener = listener;
        byte[] b = new byte[3];
        new SecureRandom().nextBytes(b);
        StringBuilder t = new StringBuilder();
        for (byte x : b) t.append(String.format("%02x", x));
        token = t.toString();
    }

    /** Startet den Server; liefert die Adresse für das Handy oder null. */
    String start() {
        String ip = lanAddress(context);
        if (ip == null) return null;
        try {
            try {
                server = new ServerSocket(PORT);
            } catch (Exception busy) {
                server = new ServerSocket(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "No server: " + e);
            return null;
        }
        running = true;
        Thread t = new Thread(this::loop, "image-server");
        t.setDaemon(true);
        t.start();
        return "http://" + ip + ":" + server.getLocalPort() + "/" + token;
    }

    void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
            // schon zu
        }
    }

    private void loop() {
        while (running) {
            try (Socket s = server.accept()) {
                s.setSoTimeout(30_000);
                handle(s);
            } catch (Exception e) {
                if (running) Log.w(TAG, "Request failed: " + e);
            }
        }
    }

    private void handle(Socket s) throws Exception {
        InputStream in = new BufferedInputStream(s.getInputStream());
        String requestLine = readLine(in);
        if (requestLine == null) return;
        int length = 0;
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                length = Integer.parseInt(line.substring(colon + 1).trim());
            }
        }
        String[] parts = requestLine.split(" ");
        String method = parts.length > 0 ? parts[0] : "";
        String path = parts.length > 1 ? parts[1] : "/";
        OutputStream out = s.getOutputStream();
        String base = "/" + token;
        if (!path.equals(base) && !path.startsWith(base + "/")) {
            respond(out, 404, "text/plain", "Not found");
            return;
        }
        if ("GET".equals(method)) {
            if (path.startsWith(base + "/current")) {
                byte[] png = currentImage();
                if (png == null) respond(out, 404, "text/plain", "Not found");
                else respond(out, 200, "image/png", png);
                return;
            }
            respond(out, 200, "text/html; charset=utf-8", page());
            return;
        }
        if (!"POST".equals(method) || length <= 0 || length > MAX_BYTES) {
            respond(out, 400, "text/plain", "Bad request");
            return;
        }
        byte[] body = readBody(in, length);
        byte[] image;
        if (path.equals(base + "/url")) {
            image = download(new String(body, StandardCharsets.UTF_8).trim());
            if (image == null) {
                respond(out, 400, "text/plain", "Could not load that address.");
                return;
            }
        } else if (path.equals(base + "/upload")) {
            image = body;
        } else {
            respond(out, 404, "text/plain", "Not found");
            return;
        }
        if (!TileImages.save(context, pkg, image)) {
            respond(out, 400, "text/plain", "That is not an image the TV can read.");
            return;
        }
        respond(out, 200, "text/plain", "Done. The tile shows the new image.");
        main.post(listener::onSaved);
    }

    /** Bild von einer http(s)-Adresse laden (höchstens 15 MB); null bei Fehler. */
    static byte[] download(String address) {
        if (!address.startsWith("http://") && !address.startsWith("https://")) return null;
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(address).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(15000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) BestSeat");
            try {
                if (con.getResponseCode() / 100 != 2) return null;
                try (InputStream in = con.getInputStream()) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] b = new byte[16384];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        buf.write(b, 0, n);
                        if (buf.size() > MAX_BYTES) return null;
                    }
                    return buf.toByteArray();
                }
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "Download failed: " + e);
            return null;
        }
    }

    /** Bild, das die Kachel gerade zeigt: eigenes Bild, sonst Banner oder Symbol der App (PNG). */
    private byte[] currentImage() {
        try {
            if (TileImages.has(context, pkg)) {
                return java.nio.file.Files.readAllBytes(TileImages.file(context, pkg).toPath());
            }
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.graphics.drawable.Drawable d = pm.getApplicationBanner(pkg);
            boolean banner = d != null;
            if (d == null) d = pm.getApplicationIcon(pkg);
            int w = 640;
            int h = 360;
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            if (banner) {
                d.setBounds(0, 0, w, h);
            } else {
                c.drawColor(android.graphics.Color.rgb(55, 55, 62));
                d.setBounds(w / 2 - 90, h / 2 - 90, w / 2 + 90, h / 2 + 90);
            }
            d.draw(c);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, buf);
            return buf.toByteArray();
        } catch (Exception e) {
            Log.w(TAG, "No current image: " + e);
            return null;
        }
    }

    private String page() {
        String name = label.replace("&", "&amp;").replace("<", "&lt;");
        // Aufbau wie Best Seat: dunkle, abgerundete Flächen, helle Knöpfe, ruhige Schrift.
        return "<!doctype html><html lang=en><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1,viewport-fit=cover'>"
                + "<meta name=theme-color content='#0b0c10'>"
                + "<title>Best Seat – " + name + "</title><style>"
                + ":root{--bg:#0b0c10;--card:rgba(38,39,46,.72);--line:rgba(255,255,255,.08);--btn:#2c2d34;"
                + "--text:#f4f4f6;--dim:rgba(244,244,246,.55);--accent:#f5a524;--ok:#34c759;--err:#ff6b5e}"
                + "*{box-sizing:border-box}html,body{margin:0}"
                + "body{min-height:100vh;font-family:Inter,-apple-system,system-ui,'Segoe UI',Roboto,sans-serif;"
                + "color:var(--text);background:radial-gradient(1200px 600px at 20% -10%,#2a2440 0,transparent 60%),"
                + "radial-gradient(900px 500px at 110% 10%,#123a44 0,transparent 55%),var(--bg);"
                + "display:flex;justify-content:center;padding:28px 18px 40px;-webkit-font-smoothing:antialiased}"
                + "main{width:100%;max-width:520px}"
                + ".brand{display:flex;align-items:center;gap:8px;font-weight:700;font-size:15px;letter-spacing:.2px;"
                + "color:var(--dim);margin:0 4px 18px}.brand b{color:var(--text);font-weight:700}"
                + ".dot{width:6px;height:6px;border-radius:50%;background:var(--accent);display:inline-block;margin:0 2px 2px}"
                + ".card{background:var(--card);border:1px solid var(--line);border-radius:24px;padding:20px;"
                + "backdrop-filter:blur(30px);-webkit-backdrop-filter:blur(30px);box-shadow:0 20px 60px rgba(0,0,0,.45)}"
                + "h1{font-size:22px;margin:2px 4px 4px;font-weight:700;letter-spacing:-.2px}"
                + ".sub{color:var(--dim);font-size:14.5px;margin:0 4px 18px;line-height:1.45}"
                + ".tile{position:relative;aspect-ratio:16/9;border-radius:16px;overflow:hidden;background:#373740;"
                + "box-shadow:0 14px 34px rgba(0,0,0,.5);margin-bottom:8px;transition:transform .25s}"
                + ".tile img{width:100%;height:100%;object-fit:cover;display:block}"
                + ".tile.new{transform:scale(1.03)}"
                + ".cap{text-align:center;color:var(--dim);font-size:13px;margin:6px 0 20px}"
                + ".label{font-size:12px;font-weight:600;letter-spacing:.8px;text-transform:uppercase;color:var(--dim);"
                + "margin:18px 6px 8px}"
                + "input[type=url]{width:100%;font:inherit;font-size:16px;color:var(--text);background:var(--btn);"
                + "border:1px solid transparent;border-radius:12px;padding:13px 14px;outline:none}"
                + "input[type=url]:focus{border-color:rgba(255,255,255,.35)}"
                + "button,.pick{display:flex;align-items:center;justify-content:center;gap:8px;width:100%;font:inherit;"
                + "font-size:16px;font-weight:600;border:0;border-radius:12px;padding:13px;margin-top:10px;cursor:pointer;"
                + "background:var(--btn);color:var(--text);transition:transform .12s,background .2s}"
                + "button:active,.pick:active{transform:scale(.98)}"
                + "button.primary{background:#f4f4f6;color:#111}button:disabled{opacity:.45}"
                + ".pick{border:1.5px dashed rgba(255,255,255,.22);background:transparent;padding:22px 13px;"
                + "flex-direction:column;gap:4px;font-weight:600}.pick small{color:var(--dim);font-weight:500;font-size:13px}"
                + ".pick.drag{border-color:var(--accent);background:rgba(245,165,36,.08)}"
                + "input[type=file]{display:none}"
                + "#msg{min-height:22px;margin:16px 4px 0;font-size:14.5px;font-weight:600;text-align:center}"
                + "#msg.ok{color:var(--ok)}#msg.err{color:var(--err)}#msg.busy{color:var(--dim)}"
                + ".foot{text-align:center;color:var(--dim);font-size:12.5px;margin-top:18px}"
                + "</style></head><body><main>"
                + "<div class=brand><b>best<span class=dot></span>seat</b>\u00b7 Tile image</div>"
                + "<div class=card><h1>" + name + "</h1>"
                + "<p class=sub>Pick a new picture for this tile. Wide images (16:9) look best.</p>"
                + "<div class=tile id=tile><img id=pre src='" + "/" + token + "/current' alt=''></div>"
                + "<div class=cap id=cap>Current tile</div>"
                + "<div class=label>From your phone</div>"
                + "<label class=pick id=pick for=f>Choose a picture<small>or drop one here</small></label>"
                + "<input id=f type=file accept='image/*'>"
                + "<button class=primary id=up disabled onclick=sendFile()>Use This Picture</button>"
                + "<div class=label>From the web</div>"
                + "<input id=u type=url inputmode=url autocomplete=off placeholder='https://…'>"
                + "<button id=go onclick=sendUrl()>Use Address</button>"
                + "<div id=msg></div></div>"
                + "<div class=foot>The page closes when you leave “Change Image” on the TV.</div>"
                + "</main><script>"
                + "const $=i=>document.getElementById(i),base=location.pathname;let file=null;"
                + "function say(t,c){$('msg').textContent=t;$('msg').className=c||''}"
                + "function preview(src,cap){$('pre').src=src;$('cap').textContent=cap;"
                + "$('tile').classList.add('new');setTimeout(()=>$('tile').classList.remove('new'),250)}"
                + "function pickFile(f){if(!f||!f.type.startsWith('image/'))return;file=f;"
                + "preview(URL.createObjectURL(f),'Preview');$('up').disabled=false;say('')}"
                + "$('f').onchange=e=>pickFile(e.target.files[0]);"
                + "const p=$('pick');['dragenter','dragover'].forEach(n=>p.addEventListener(n,e=>{e.preventDefault();p.classList.add('drag')}));"
                + "['dragleave','drop'].forEach(n=>p.addEventListener(n,e=>{e.preventDefault();p.classList.remove('drag')}));"
                + "p.addEventListener('drop',e=>pickFile(e.dataTransfer.files[0]));"
                + "function done(r){return r.text().then(t=>{if(r.ok){say('✓ '+t,'ok');"
                + "preview(base+'/current?'+Date.now(),'Current tile');$('up').disabled=true;file=null}else say(t,'err')})}"
                + "function fail(){say('The TV did not answer. Is “Change Image” still open?','err')}"
                + "function sendFile(){if(!file)return;say('Sending…','busy');"
                + "fetch(base+'/upload',{method:'POST',body:file}).then(done).catch(fail)}"
                + "function sendUrl(){const v=$('u').value.trim();if(!v)return;say('Loading…','busy');"
                + "fetch(base+'/url',{method:'POST',body:/^https?:\\/\\//.test(v)?v:'https://'+v}).then(done).catch(fail)}"
                + "$('u').addEventListener('keydown',e=>{if(e.key==='Enter')sendUrl()});"
                + "</script></body></html>";
    }

    private static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) return null;
        }
        return c == -1 && sb.length() == 0 ? null : sb.toString();
    }

    private static byte[] readBody(InputStream in, int length) throws Exception {
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) break;
            read += n;
        }
        return body;
    }

    private static void respond(OutputStream out, int code, String type, String text) throws Exception {
        respond(out, code, type, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(OutputStream out, int code, String type, byte[] body) throws Exception {
        String status = code == 200 ? "OK" : code == 404 ? "Not Found" : "Bad Request";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    /** IPv4-Adresse im Heimnetz (nicht die des VPN). */
    static String lanAddress(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (LinkAddress a : lp.getLinkAddresses()) {
                    InetAddress addr = a.getAddress();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) return addr.getHostAddress();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "No LAN address: " + e);
        }
        return null;
    }
}
