package com.charlie.ai.domain.ssh.service.security.parser;

import java.util.regex.Pattern;

/**
 * 命令文本归一化器
 * <p>
 * 职责单一：把"看起来一样、实际不同"的命令折叠成同一种形态，
 * 消除排版与编码层面的绕过。归一化只做<b>等价变换</b>，不改变命令语义。
 * <p>
 * 处理三件事：
 * <ol>
 *     <li>剥离 ANSI 控制序列（颜色、光标移动等）——模型回显常带颜色码；</li>
 *     <li>全角标点转半角——中文模型常输出全角 {@code ；｜＆＞}，这是最常见的低成本的绕过手法；</li>
 *     <li>反斜杠续行折叠为空格——{@code rm -rf \n /} 属于同一条命令。</li>
 * </ol>
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public class CommandNormalizer {

    /** 终端 ANSI 控制序列 */
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[0-9;?]*[ -/]*[@-~]");

    /**
     * 归一化命令文本
     *
     * @param command 原始命令
     * @return 归一化后的命令；入参为 null 时返回空串
     */
    public String normalize(String command) {
        if (command == null) {
            return "";
        }
        String s = ANSI_PATTERN.matcher(command).replaceAll("");
        s = fullWidthToHalfWidth(s);
        s = s.replace("\\\r\n", " ").replace("\\\n", " ");
        return s.trim();
    }

    /**
     * 全角标点转半角
     */
    private String fullWidthToHalfWidth(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '；':
                    sb.append(';');
                    break;
                case '｜':
                    sb.append('|');
                    break;
                case '＆':
                    sb.append('&');
                    break;
                case '＞':
                    sb.append('>');
                    break;
                case '＜':
                    sb.append('<');
                    break;
                case '（':
                    sb.append('(');
                    break;
                case '）':
                    sb.append(')');
                    break;
                case '＇':
                    sb.append('\'');
                    break;
                case '＂':
                    sb.append('"');
                    break;
                case '：':
                    sb.append(':');
                    break;
                case '　':
                    sb.append(' ');
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

}
