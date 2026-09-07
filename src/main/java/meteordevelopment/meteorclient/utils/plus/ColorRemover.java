package meteordevelopment.meteorclient.utils.plus;

public class ColorRemover {
    public static String removeColorCodes(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)\u00a7[0-9A-FK-OR]", "");
    }

    public static String getVerbatim(String input) {
        return removeColorCodes(input);
    }
}
