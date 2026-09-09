import com.rafambn.kflate.KFlate;
import com.rafambn.kflate.compression.Raw;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.Inflater;

public class RatioSweep {
    public static void main(String[] args) throws Exception {
        String[] names = {"simpleText", "text", "model3D", "Rainier.bmp", "Maltese.bmp", "Sunrise.bmp", "compressed_MVT.pbf"};
        int first = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        int last = args.length > 2 ? Integer.parseInt(args[2]) : 9;
        for (String name : names) {
            byte[] input = Files.readAllBytes(Path.of(args[0], name));
            for (int level = first; level <= last; level++) {
                long start = System.nanoTime();
                byte[] output = KFlate.INSTANCE.compress(input, new Raw(level, null));
                double ms = (System.nanoTime() - start) / 1e6;
                Inflater inflater = new Inflater(true);
                try {
                    inflater.setInput(output);
                    byte[] decoded = new byte[input.length + 1];
                    int count = inflater.inflate(decoded);
                    if (!inflater.finished() || count != input.length || !Arrays.equals(input, Arrays.copyOf(decoded, count))) {
                        throw new AssertionError("Invalid output: " + name + " level " + level);
                    }
                } finally {
                    inflater.end();
                }
                if (!Arrays.equals(input, KFlate.INSTANCE.decompress(output, new com.rafambn.kflate.decompression.Raw(null, null)))) {
                    throw new AssertionError("KFlate round trip: " + name + " level " + level);
                }
                System.out.printf(java.util.Locale.ROOT, "{\"corpus\":\"%s\",\"level\":%d,\"originalSizeBytes\":%d,\"compressedSizeBytes\":%d,\"singleRunMs\":%.6f,\"sha256\":\"%s\"}%n", name, level, input.length, output.length, ms, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(output)));
            }
        }
    }
}
