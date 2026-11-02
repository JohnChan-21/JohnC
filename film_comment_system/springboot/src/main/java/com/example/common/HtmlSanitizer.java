package com.example.common;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HTML 净化工具：用户提交的评论内容在入库前统一净化，防止存储型 XSS。
 * 采用白名单策略，是富文本 XSS 的权威防线（前端 v-html 只渲染净化后的内容）。
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {
    }

    /**
     * 长评（富文本）：保留编辑器输出的排版标签与内联样式。
     * 白名单依据 wangEditor 5 的实际产物（p / div / span / img / a 等）。
     * 脚本标签、on* 事件属性、javascript: 等危险协议不在白名单内，会被直接剔除；
     * relaxed() 已限定 img[src] 仅 http(s)、a[href] 仅 http(s)/ftp/mailto。
     */
    private static final Safelist RICH_TEXT = Safelist.relaxed()
            // 补全 relaxed() 未包含、但编辑器会输出的排版标签
            .addTags("hr", "s", "font")
            // 编辑器用内联 style / class 控制颜色、字号、对齐，需放行（on* 事件属性不放行，天然被剔除）
            .addAttributes(":all", "style", "class");

    /** 短评（纯文本）：不放行任何标签，全部转义，杜绝 HTML 注入。 */
    private static final Safelist PLAIN_TEXT = Safelist.none();

    /**
     * 净化富文本（长评）
     *
     * @param html 原始富文本
     * @return 仅含白名单标签/属性的安全 HTML
     */
    public static String cleanRichText(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return Jsoup.clean(html, RICH_TEXT);
    }

    /**
     * 净化纯文本（短评）
     *
     * @param text 原始文本
     * @return 已转义的纯文本
     */
    public static String cleanPlainText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Jsoup.clean(text, PLAIN_TEXT);
    }
}