package com.openapi.ks.myapp.view;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LaTeXFormulaReplacer {
    private static String replaceMathDelimiters(String input) {
        // 替换 \[ ... \] 为 $$ ... $$
        String result = replaceDelimiter(input, "\\[", "\\]", "$$");
        // 替换 \( ... \) 为 $ ... $
        result = replaceDelimiter(result, "\\(", "\\)", "$");
        return result;
    }

    private static String replaceDelimiter(String input, String startDelimiter, String endDelimiter, String replacementDelimiter) {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        while (index < input.length()) {
            int startIndex = input.indexOf(startDelimiter, index);
            if (startIndex == -1) {
                sb.append(input.substring(index));
                break;
            }
            sb.append(input, index, startIndex);
            int endIndex = input.indexOf(endDelimiter, startIndex + startDelimiter.length());
            if (endIndex == -1) {
                sb.append(input.substring(startIndex));
                break;
            }
            sb.append(replacementDelimiter);
            sb.append(input, startIndex + startDelimiter.length(), endIndex);
            sb.append(replacementDelimiter);
            index = endIndex + endDelimiter.length();
        }
        return sb.toString();
    }
    private static String replaceString(String input) {
        StringBuilder result = new StringBuilder();
        input = input.replace("`$`","!MY010MY!").replace("`$$`","!!MY010010MY!!");
        int i = 0;
        while (i < input.length()) {
            if (i + 1 < input.length() && input.charAt(i) == '$' && input.charAt(i + 1) == '$') {
                // 处理双$情况
                int endIndex = input.indexOf("$$", i + 2);
                if (endIndex != -1) {
                    result.append(input, i, endIndex + 2);
                    i = endIndex + 2;
                } else {
                    result.append(input.charAt(i));
                    i++;
                }
            } else if (input.charAt(i) == '$') {
                // 处理单$情况
                int endIndex = input.indexOf('$', i + 1);
                if (endIndex != -1) {
                    result.append('$');
                    result.append(input, i + 1, endIndex);
                    result.append('$');
                    i = endIndex + 1;
                } else {
                    result.append(input.charAt(i));
                    i++;
                }

            } else {
                result.append(input.charAt(i));
                i++;
            }
        }
        return result.toString().replace("!MY010MY!", "`$`").replace("!!MY010010MY!!","`$$`");
    }
    public static String replaceBrackets(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = replaceMathDelimiters(input);
        result = replaceString(result);
        return result;
    }

    public static void main(String[] args) {
        List<String> t = Arrays.asList("$$dddddddae  d \facdddddd$$","$dddddddae  d \facdddddd$","$dddddddae  d \facdddddd","$$dddddddae  d \facdddddd");
        Pattern RE = Pattern.compile("(?<!\\$)\\$([^$]+)\\$(?!\\$)");
        t.forEach(i ->{
            Matcher matcher = RE.matcher(i);
            boolean n = matcher.find();
            System.out.println(i+"->"+n);

        });

    }
}
