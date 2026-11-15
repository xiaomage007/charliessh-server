package com.charlie.ai.domain.ssh.service.security.parser;

import com.charlie.ai.domain.ssh.model.entity.ParsedCommand;
import com.charlie.ai.domain.ssh.model.entity.ParsedScript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shell 命令解析器
 * <p>
 * 把一段原始命令文本解析成结构化的 {@link ParsedScript}，供规则层判定。
 * 本类是<b>纯函数式</b>的：不做任何风险判断，只回答"这条命令实际由哪些子命令组成、
 * 每条子命令的基础命令名是什么、参数与重定向目标有哪些"。
 * <p>
 * 关键设计：先<b>拆分</b>再<b>解析</b>。
 * <ul>
 *     <li>拆分：按 {@code ;}、{@code |}、{@code &&}、{@code ||}、{@code &}、换行切分，
 *         引号内的分隔符不参与拆分；{@code $( )} 与反引号中的命令单独抽出；</li>
 *     <li>解析：逐条剥离包装命令（{@code sudo} / {@code env} / {@code xargs} 等），
 *         得出真正被执行的基础命令名。</li>
 * </ul>
 * 这样 {@code ps aux | grep rm}、{@code echo "rm -rf /"} 的基础命令名分别是
 * {@code ps}、{@code echo}，不会误判；而 {@code sudo rm -rf /}、
 * {@code find . | xargs rm -rf} 能正确还原出 {@code rm}，不会漏判。
 *
 * @Author: Charlie
 * @Date: 2026/11/15
 */
public class ShellCommandParser {

    /** 嵌套命令递归解析的最大深度，防止 {@code bash -c "bash -c ..."} 造成栈溢出 */
    public static final int MAX_NESTED_DEPTH = 3;

    /**
     * shell 命令替换：{@code $(...)} 或反引号
     */
    private static final Pattern CMD_SUBST_PATTERN = Pattern.compile("\\$\\(([^()]*)\\)|`([^`]*)`");

    /**
     * 重定向目标：{@code > file} / {@code >> file}
     * <p>
     * 字符类排除了 {@code &}，因此 {@code 2>&1} 这类 fd 重定向不会被误当成文件写入。
     */
    private static final Pattern REDIRECT_PATTERN = Pattern.compile(">>?\\s*([^\\s;|&<>]+)");

    /**
     * 环境变量赋值：{@code FOO=bar}
     */
    private static final Pattern ENV_ASSIGN_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*=.*$");

    /**
     * 包装命令：本身不改变语义，只在前面带参数
     */
    private static final Set<String> WRAPPER_COMMANDS = new HashSet<>(Arrays.asList(
            "sudo", "doas", "nohup", "time", "exec", "command", "builtin",
            "nice", "ionice", "stdbuf", "setsid", "watch", "env", "xargs"));

    /**
     * 可执行 shell：命中 {@code -c} 时内层命令需要递归解析
     */
    private static final Set<String> SHELL_COMMANDS = new HashSet<>(Arrays.asList(
            "sh", "bash", "zsh", "dash", "ksh", "csh", "tcsh"));

    /**
     * xargs 中需要吃掉一个参数的选项
     */
    private static final Set<String> XARGS_OPTS_WITH_ARG = new HashSet<>(Arrays.asList(
            "-I", "-P", "-n", "-L", "-d", "-s", "--max-args", "--max-chars", "--replace", "--delimiter"));

    /**
     * sudo/doas 中需要吃掉一个参数的选项
     */
    private static final Set<String> SUDO_OPTS_WITH_ARG = new HashSet<>(Arrays.asList(
            "-u", "-g", "-p", "-C", "-h", "-U", "--user", "--group", "--prompt", "--chdir", "--close-from"));

    private final CommandNormalizer normalizer;

    public ShellCommandParser() {
        this(new CommandNormalizer());
    }

    public ShellCommandParser(CommandNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    /**
     * 解析一条完整命令
     *
     * @param command 原始命令
     * @return 解析结果；命令为空时 commands 为空列表
     */
    public ParsedScript parse(String command) {
        String normalized = normalizer.normalize(command);
        List<ParsedCommand> commands = new ArrayList<>();
        collect(normalized, commands, 0);
        return new ParsedScript(normalized, commands);
    }

    /**
     * 递归收集待检命令（含嵌套命令）
     */
    private void collect(String text, List<ParsedCommand> out, int depth) {
        for (String segment : splitCommands(text)) {
            ParsedCommand parsed = parseSegment(segment);
            if (parsed == null) {
                continue;
            }
            out.add(parsed);
            if (depth < MAX_NESTED_DEPTH) {
                for (String nested : parsed.getNestedScripts()) {
                    collect(nested, out, depth + 1);
                }
            }
        }
    }

    /**
     * 把整条命令拆成子命令
     * <p>
     * 分隔符：{@code ;}、换行、{@code |}、{@code &&}、{@code ||}、{@code &}（后台执行）。
     * 引号内的分隔符不参与拆分；同时把 {@code $( )} 与反引号中的命令单独抽出来。
     */
    private List<String> splitCommands(String command) {
        List<String> segments = new ArrayList<>();

        // 抽出命令替换内容，单独作为子命令检测
        Matcher subst = CMD_SUBST_PATTERN.matcher(command);
        while (subst.find()) {
            String inner = subst.group(1) != null ? subst.group(1) : subst.group(2);
            if (inner != null && !inner.isBlank()) {
                segments.add(inner.trim());
            }
        }

        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == '\\' && i + 1 < command.length()) {
                current.append(c).append(command.charAt(++i));
                continue;
            }
            if (c == ';' || c == '\n' || c == '\r' || c == '|') {
                segments.add(current.toString());
                current.setLength(0);
                continue;
            }
            if (c == '&') {
                // && 与 >& 都不是分隔符
                if (i + 1 < command.length() && command.charAt(i + 1) == '&') {
                    segments.add(current.toString());
                    current.setLength(0);
                    i++;
                    continue;
                }
                if (i + 1 < command.length() && command.charAt(i + 1) == '>') {
                    current.append(c);
                    continue;
                }
                if (i > 0 && command.charAt(i - 1) == '>') {
                    current.append(c);
                    continue;
                }
                segments.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        segments.add(current.toString());

        List<String> result = new ArrayList<>(segments.size());
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 解析一条子命令，剥离包装命令，得出真正要执行的基础命令
     */
    private ParsedCommand parseSegment(String segment) {
        List<String> tokens = tokenize(segment);
        if (tokens.isEmpty()) {
            return null;
        }
        List<String> redirects = extractRedirectTargets(segment);

        int idx = skipAssignmentsAndWrappers(tokens, 0);
        if (idx >= tokens.size()) {
            return null;
        }

        String head = basename(tokens.get(idx).toLowerCase(Locale.ROOT));
        List<String> args = new ArrayList<>(tokens.subList(idx + 1, tokens.size()));

        // xargs：真正被执行的命令在参数区，例如 find . | xargs rm -rf
        if ("xargs".equals(head)) {
            int k = 0;
            while (k < args.size() && args.get(k).startsWith("-")) {
                String opt = args.get(k);
                k++;
                if (XARGS_OPTS_WITH_ARG.contains(opt) && k < args.size()) {
                    k++;
                }
            }
            if (k < args.size()) {
                head = basename(args.get(k).toLowerCase(Locale.ROOT));
                args = new ArrayList<>(args.subList(k + 1, args.size()));
            }
        }

        return new ParsedCommand(segment, head, args, redirects, extractNestedScripts(head, args));
    }

    /**
     * 提取嵌套脚本：{@code bash -c "xxx"} 与 {@code eval "xxx"}
     */
    private List<String> extractNestedScripts(String base, List<String> args) {
        List<String> nested = new ArrayList<>();
        if (SHELL_COMMANDS.contains(base)) {
            int cIndex = args.indexOf("-c");
            if (cIndex >= 0 && cIndex + 1 < args.size()) {
                nested.add(args.get(cIndex + 1));
            }
        } else if ("eval".equals(base) && !args.isEmpty()) {
            nested.add(String.join(" ", args));
        }
        return nested;
    }

    /**
     * 跳过环境变量赋值与包装命令，返回有效命令的起始下标
     */
    private int skipAssignmentsAndWrappers(List<String> tokens, int from) {
        int idx = from;
        while (idx < tokens.size()) {
            String token = tokens.get(idx);
            if (ENV_ASSIGN_PATTERN.matcher(token).matches()) {
                idx++;
                continue;
            }
            String low = basename(token.toLowerCase(Locale.ROOT));
            if (!WRAPPER_COMMANDS.contains(low)) {
                break;
            }
            idx++;
            if ("sudo".equals(low) || "doas".equals(low)) {
                while (idx < tokens.size() && tokens.get(idx).startsWith("-")) {
                    String opt = tokens.get(idx);
                    idx++;
                    if (SUDO_OPTS_WITH_ARG.contains(opt) && idx < tokens.size()) {
                        idx++;
                    }
                }
            } else if ("env".equals(low)) {
                while (idx < tokens.size() && tokens.get(idx).startsWith("-")) {
                    idx++;
                }
            }
        }
        return idx;
    }

    /**
     * 分词：空白分隔，识别引号与转义，并剥掉包裹引号
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                    current.append(c);
                    continue;
                }
                current.append(c);
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == '\\' && i + 1 < text.length()) {
                current.append(c).append(text.charAt(++i));
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(unquote(current.toString()));
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            tokens.add(unquote(current.toString()));
        }
        return tokens;
    }

    /**
     * 去掉包裹引号
     */
    private String unquote(String token) {
        if (token.length() >= 2) {
            char first = token.charAt(0);
            char last = token.charAt(token.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return token.substring(1, token.length() - 1);
            }
        }
        return token;
    }

    /**
     * 提取路径中的文件名部分：{@code /bin/rm} → {@code rm}
     */
    private String basename(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /**
     * 提取重定向写入的目标
     */
    private List<String> extractRedirectTargets(String segment) {
        List<String> targets = new ArrayList<>();
        Matcher matcher = REDIRECT_PATTERN.matcher(segment);
        while (matcher.find()) {
            String target = unquote(matcher.group(1));
            if (!target.isEmpty() && !target.startsWith("&")) {
                targets.add(target);
            }
        }
        return targets;
    }

}
