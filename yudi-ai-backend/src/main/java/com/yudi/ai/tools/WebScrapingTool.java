package com.yudi.ai.tools;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.http.HtmlUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 网页抓取工具（优化版）
 */
public class WebScrapingTool {

    private static final int MAX_CONTENT_LENGTH = 20000; // 增加最大内容长度限制
    private static final int READ_TIMEOUT = 15000; // 读取超时15秒
    private static final int MIN_TEXT_LENGTH = 5; // 最小文本长度
    private static final int MIN_PARAGRAPH_LENGTH = 10; // 最小段落长度

    @Tool(description = "Scrape the content of a web page and extract useful text information")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            // 验证URL格式
            if (StrUtil.isBlank(url)) {
                return "错误: URL不能为空";
            }

            // 规范化URL
            url = StrUtil.trim(url);
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            // 连接网页，设置超时和User-Agent
            Document document = Jsoup.connect(url)
                    .timeout(READ_TIMEOUT)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .get();

            // 克隆document以便进行不同的处理
            Document cleanDoc = document.clone();

            // 智能移除无用元素
            removeUselessElements(cleanDoc);

            // 使用Hutool的StrBuilder构建内容
            StrBuilder content = new StrBuilder();

            // 1. 提取页面基础信息
            extractBasicInfo(document, content);

            // 2. 提取页面标题结构
            extractHeadings(cleanDoc, content);

            // 3. 智能提取主要内容
            extractMainContent(cleanDoc, content);

            // 4. 提取列表内容
            extractLists(cleanDoc, content);

            // 5. 提取表格内容
            extractTables(cleanDoc, content);

            // 6. 提取交互元素
            extractInteractiveElements(cleanDoc, content);

            // 7. 提取媒体信息
            extractMediaInfo(cleanDoc, content);

            // 8. 提取链接
            extractLinks(cleanDoc, content, url);

            // 9. 提取其他有用信息
            extractAdditionalInfo(cleanDoc, content);

            // 获取结果
            String result = content.toString();

            // 如果内容太少，尝试获取更多内容
            if (result.length() < 300) {
                String bodyText = extractFullText(document.body());
                if (StrUtil.isNotBlank(bodyText)) {
                    content.append("\n\n【页面全文（补充）】\n");
                    content.append(formatText(bodyText, 2000));
                    result = content.toString();
                }
            }

            // 清理和格式化最终结果
            result = cleanFinalResult(result);

            // 智能截断
            if (result.length() > MAX_CONTENT_LENGTH) {
                result = smartTruncate(result, MAX_CONTENT_LENGTH);
            }

            return StrUtil.isBlank(result) ? "无法从网页中提取到有效内容" : result;

        } catch (java.net.SocketTimeoutException e) {
            return "错误: 网页连接超时，请检查网络连接或URL是否正确";
        } catch (java.net.UnknownHostException e) {
            return "错误: 无法解析域名，请检查URL是否正确";
        } catch (Exception e) {
            return StrUtil.format("错误: 抓取网页失败 - {}", e.getMessage());
        }
    }

    /**
     * 智能移除无用元素
     */
    private void removeUselessElements(Document doc) {
        // 移除脚本和样式
        doc.select("script, style, noscript, iframe, embed, object, applet").remove();

        // 移除广告相关元素（使用更智能的选择器）
        doc.select("[class*=advert], [id*=advert], [class*=sponsor], [id*=sponsor]").remove();
        doc.select("[class*=popup], [id*=popup], [class*=modal], [id*=modal]").remove();

        // 移除隐藏元素
        doc.select("[style*='display:none'], [style*='display: none'], [hidden]").remove();

        // 移除注释相关元素（但保留主要内容区域的评论）
        doc.select("div[class*=comment]:not([class*=content]), div[id*=comment]:not([id*=content])").remove();
    }

    /**
     * 提取页面基础信息
     */
    private void extractBasicInfo(Document doc, StrBuilder content) {
        // 标题
        String title = StrUtil.trim(doc.title());
        if (StrUtil.isNotBlank(title)) {
            content.append("【页面标题】\n").append(title).append("\n\n");
        }

        // Meta描述
        String description = getMetaContent(doc, "description", "og:description", "twitter:description");
        if (StrUtil.isNotBlank(description)) {
            content.append("【页面描述】\n").append(HtmlUtil.cleanHtmlTag(description)).append("\n\n");
        }

        // Meta关键词
        String keywords = getMetaContent(doc, "keywords");
        if (StrUtil.isNotBlank(keywords)) {
            content.append("【关键词】\n").append(keywords).append("\n\n");
        }

        // 作者信息
        String author = getMetaContent(doc, "author", "article:author");
        if (StrUtil.isNotBlank(author)) {
            content.append("【作者】").append(author).append("\n\n");
        }

        // 发布时间
        String publishTime = getMetaContent(doc, "publish_time", "article:published_time", "datePublished");
        if (StrUtil.isNotBlank(publishTime)) {
            content.append("【发布时间】").append(publishTime).append("\n\n");
        }
    }

    /**
     * 获取Meta标签内容
     */
    private String getMetaContent(Document doc, String... names) {
        for (String name : names) {
            Elements elements = doc.select(StrUtil.format("meta[name='{}'], meta[property='{}'], meta[itemprop='{}']",
                    name, name, name));
            if (CollUtil.isNotEmpty(elements)) {
                String content = elements.first().attr("content");
                if (StrUtil.isNotBlank(content)) {
                    return StrUtil.trim(content);
                }
            }
        }
        return "";
    }

    /**
     * 提取标题结构
     */
    private void extractHeadings(Document doc, StrBuilder content) {
        Elements headings = doc.select("h1, h2, h3, h4, h5, h6");
        if (CollUtil.isNotEmpty(headings)) {
            content.append("【页面结构】\n");

            List<String> headingTexts = new ArrayList<>();
            for (Element heading : headings) {
                String text = cleanText(heading.text());
                if (StrUtil.isNotBlank(text) && text.length() < 200) {
                    int level = 1;
                    try {
                        // 安全地解析 h1~h6 中的数字层级
                        level = Integer.parseInt(heading.tagName().substring(1));
                    } catch (NumberFormatException ignored) {
                    }

                    // 根据层级缩进
                    String indent = StrUtil.repeat("  ", Math.max(0, level - 1));
                    String headingLine = StrUtil.format("{}• {}", indent, text);

                    if (!headingTexts.contains(headingLine)) { // 去重
                        headingTexts.add(headingLine);
                        content.append(headingLine).append("\n");
                    }
                }
            }
            content.append("\n");
        }
    }


    /**
     * 提取主要内容
     */
    private void extractMainContent(Document doc, StrBuilder content) {
        content.append("【主要内容】\n");

        // 智能查找主要内容区域
        String[] contentSelectors = {
                "article", "main", "[role=main]", "[class*=content]:not([class*=sidebar])",
                "[id*=content]:not([id*=sidebar])", "section[class*=body]", "div[class*=article]",
                "div[class*=post]:not([class*=sidebar])", "div[class*=text]", "div[class*=story]"
        };

        Set<String> extractedTexts = new HashSet<>();
        boolean foundContent = false;

        for (String selector : contentSelectors) {
            Elements elements = doc.select(selector);
            for (Element element : elements) {
                String elementText = extractElementContent(element, extractedTexts);
                if (StrUtil.isNotBlank(elementText)) {
                    content.append(elementText).append("\n");
                    foundContent = true;
                }
            }
            if (foundContent && content.length() > 1000) {
                break; // 已找到足够内容
            }
        }

        // 如果没找到主要内容，提取所有段落
        if (!foundContent) {
            Elements paragraphs = doc.select("p");
            for (Element p : paragraphs) {
                String text = cleanText(p.text());
                if (text.length() > MIN_PARAGRAPH_LENGTH && extractedTexts.add(text)) {
                    content.append(text).append("\n\n");
                }
            }
        }

        content.append("\n");
    }

    /**
     * 提取元素内容
     */
    private String extractElementContent(Element element, Set<String> extractedTexts) {
        StrBuilder result = new StrBuilder();

        // 提取段落
        Elements paragraphs = element.select("p");
        for (Element p : paragraphs) {
            String text = cleanText(p.text());
            if (text.length() > MIN_TEXT_LENGTH && extractedTexts.add(text)) {
                result.append(text).append("\n\n");
            }
        }

        // 如果没有段落，提取文本块
        if (result.isEmpty()) {
            Elements textBlocks = element.select("div, span, section");
            for (Element block : textBlocks) {
                String text = cleanText(block.ownText());
                if (text.length() > MIN_PARAGRAPH_LENGTH && extractedTexts.add(text)) {
                    result.append(text).append("\n\n");
                }
            }
        }

        return result.toString();
    }

    /**
     * 提取列表内容
     */
    private void extractLists(Document doc, StrBuilder content) {
        Elements lists = doc.select("ul, ol, dl");
        if (CollUtil.isNotEmpty(lists)) {
            StrBuilder listContent = new StrBuilder();
            Set<String> uniqueItems = new HashSet<>();

            for (Element list : lists) {
                Elements items = list.select("li, dt, dd");
                for (Element item : items) {
                    String text = cleanText(item.text());
                    if (text.length() > MIN_TEXT_LENGTH && uniqueItems.add(text)) {
                        String prefix = list.tagName().equals("ol") ? "1. " : "• ";
                        listContent.append(prefix).append(StrUtil.maxLength(text, 200)).append("\n");
                    }
                }
            }

            if (!listContent.isEmpty()) {
                content.append("【列表内容】\n").append(listContent).append("\n");
            }
        }
    }

    /**
     * 提取表格内容
     */
    private void extractTables(Document doc, StrBuilder content) {
        Elements tables = doc.select("table");
        if (CollUtil.isNotEmpty(tables)) {
            content.append("【表格数据】\n");

            for (Element table : tables) {
                // 提取表格标题
                Elements caption = table.select("caption");
                if (CollUtil.isNotEmpty(caption)) {
                    content.append("表格: ").append(cleanText(caption.text())).append("\n");
                }

                // 提取表格内容
                Elements rows = table.select("tr");
                int rowCount = 0;
                for (Element row : rows) {
                    if (rowCount++ > 20) break; // 限制行数

                    Elements cells = row.select("th, td");
                    List<String> cellTexts = cells.stream()
                            .map(cell -> StrUtil.maxLength(cleanText(cell.text()), 50))
                            .filter(StrUtil::isNotBlank)
                            .collect(Collectors.toList());

                    if (CollUtil.isNotEmpty(cellTexts)) {
                        content.append(CollUtil.join(cellTexts, " | ")).append("\n");
                    }
                }
                content.append("\n");
            }
        }
    }

    /**
     * 提取交互元素
     */
    private void extractInteractiveElements(Document doc, StrBuilder content) {
        StrBuilder interContent = new StrBuilder();

        // 提取输入框
        Elements inputs = doc.select("input[type=text], input[type=search], textarea, select");
        Set<String> uniqueInputs = new HashSet<>();
        for (Element input : inputs) {
            String placeholder = input.attr("placeholder");
            String label = findLabelForInput(doc, input);
            String inputDesc = StrUtil.isNotBlank(label) ? label : placeholder;

            if (StrUtil.isNotBlank(inputDesc) && uniqueInputs.add(inputDesc)) {
                interContent.append("📝 输入框: ").append(inputDesc).append("\n");
            }
        }

        // 提取按钮
        Elements buttons = doc.select("button, input[type=submit], input[type=button], a[class*=btn]");
        Set<String> uniqueButtons = new HashSet<>();
        for (Element button : buttons) {
            String buttonText = StrUtil.isNotBlank(button.text()) ? button.text() : button.attr("value");
            buttonText = cleanText(buttonText);

            if (StrUtil.isNotBlank(buttonText) && buttonText.length() < 50 && uniqueButtons.add(buttonText)) {
                interContent.append("🔘 按钮: ").append(buttonText).append("\n");
            }
        }

        if (!interContent.isEmpty()) {
            content.append("【交互元素】\n").append(interContent).append("\n");
        }
    }

    /**
     * 查找输入框的标签
     */
    private String findLabelForInput(Document doc, Element input) {
        String inputId = input.attr("id");
        if (StrUtil.isNotBlank(inputId)) {
            Elements labels = doc.select(StrUtil.format("label[for='{}']", inputId));
            if (CollUtil.isNotEmpty(labels)) {
                return cleanText(labels.first().text());
            }
        }

        // 查找父元素中的label
        Element parent = input.parent();
        if (parent != null) {
            Elements labels = parent.select("label");
            if (CollUtil.isNotEmpty(labels)) {
                return cleanText(labels.first().text());
            }
        }

        return input.attr("name");
    }

    /**
     * 提取媒体信息
     */
    private void extractMediaInfo(Document doc, StrBuilder content) {
        StrBuilder mediaContent = new StrBuilder();

        // 提取图片信息
        Elements images = doc.select("img[alt], img[title]");
        Set<String> uniqueImages = new HashSet<>();
        int imgCount = 0;
        for (Element img : images) {
            String altText = StrUtil.blankToDefault(img.attr("alt"), img.attr("title"));
            altText = cleanText(altText);

            if (StrUtil.isNotBlank(altText) && uniqueImages.add(altText) && imgCount++ < 10) {
                mediaContent.append("🖼️ ").append(altText).append("\n");
            }
        }

        // 提取视频信息
        Elements videos = doc.select("video, iframe[src*=video], iframe[src*=youtube], iframe[src*=vimeo]");
        if (CollUtil.isNotEmpty(videos)) {
            mediaContent.append("🎬 页面包含").append(videos.size()).append("个视频元素\n");
        }

        // 提取音频信息
        Elements audios = doc.select("audio");
        if (CollUtil.isNotEmpty(audios)) {
            mediaContent.append("🔊 页面包含").append(audios.size()).append("个音频元素\n");
        }

        if (!mediaContent.isEmpty()) {
            content.append("【媒体信息】\n").append(mediaContent).append("\n");
        }
    }

    /**
     * 提取链接
     */
    private void extractLinks(Document doc, StrBuilder content, String currentUrl) {
        Elements links = doc.select("a[href]");
        if (CollUtil.isNotEmpty(links)) {
            Map<String, String> uniqueLinks = new LinkedHashMap<>();

            for (Element link : links) {
                String href = link.attr("abs:href");
                String linkText = cleanText(link.text());

                // 过滤条件
                if (StrUtil.isBlank(href) || StrUtil.isBlank(linkText) ||
                        linkText.length() < 2 || linkText.length() > 100 ||
                        href.contains("javascript:") || href.contains("mailto:") ||
                        href.equals(currentUrl) || href.contains("#")) {
                    continue;
                }

                // 分类链接
                String category = categorizeLlink(href, linkText);
                String key = category + linkText;

                if (!uniqueLinks.containsKey(key) && uniqueLinks.size() < 30) {
                    uniqueLinks.put(key, StrUtil.format("{} {} → {}",
                            category, linkText, StrUtil.maxLength(href, 100)));
                }
            }

            if (CollUtil.isNotEmpty(uniqueLinks)) {
                content.append("【相关链接】\n");
                uniqueLinks.values().forEach(link -> content.append(link).append("\n"));
                content.append("\n");
            }
        }
    }

    /**
     * 分类链接
     */
    private String categorizeLlink(String href, String text) {
        if (ReUtil.contains("download|下载", text.toLowerCase())) {
            return "📥";
        } else if (ReUtil.contains("github|git", href.toLowerCase())) {
            return "💻";
        } else if (ReUtil.contains("doc|文档|wiki|help|帮助", text.toLowerCase())) {
            return "📚";
        } else if (ReUtil.contains("twitter|facebook|weibo|linkedin", href.toLowerCase())) {
            return "🔗";
        } else {
            return "➤";
        }
    }

    /**
     * 提取其他有用信息
     */
    private void extractAdditionalInfo(Document doc, StrBuilder content) {
        // 提取代码块
        Elements codeBlocks = doc.select("pre, code");
        if (CollUtil.isNotEmpty(codeBlocks)) {
            content.append("【代码片段】\n");
            int codeCount = 0;
            for (Element code : codeBlocks) {
                if (codeCount++ >= 5) break;
                String codeText = code.text();
                if (StrUtil.isNotBlank(codeText) && codeText.length() > 10) {
                    content.append("```\n").append(StrUtil.maxLength(codeText, 500)).append("\n```\n\n");
                }
            }
        }

        // 提取引用
        Elements quotes = doc.select("blockquote, q, cite");
        if (CollUtil.isNotEmpty(quotes)) {
            content.append("【引用内容】\n");
            for (Element quote : quotes) {
                String quoteText = cleanText(quote.text());
                if (StrUtil.isNotBlank(quoteText) && quoteText.length() > 10) {
                    content.append("💬 ").append(StrUtil.maxLength(quoteText, 300)).append("\n\n");
                }
            }
        }

        // 提取定义列表
        Elements definitions = doc.select("dl");
        if (CollUtil.isNotEmpty(definitions)) {
            content.append("【定义列表】\n");
            for (Element dl : definitions) {
                Elements terms = dl.select("dt");
                Elements descs = dl.select("dd");
                for (int i = 0; i < Math.min(terms.size(), descs.size()); i++) {
                    String term = cleanText(terms.get(i).text());
                    String desc = cleanText(descs.get(i).text());
                    if (StrUtil.isNotBlank(term) && StrUtil.isNotBlank(desc)) {
                        content.append("📌 ").append(term).append(": ").append(desc).append("\n");
                    }
                }
            }
            content.append("\n");
        }
    }

    /**
     * 提取完整文本
     */
    private String extractFullText(Element element) {
        if (element == null) {
            return "";
        }

        // 移除脚本和样式的文本
        Elements scripts = element.select("script, style");
        scripts.remove();

        String text = element.text();
        return cleanText(text);
    }

    /**
     * 格式化文本
     */
    private String formatText(String text, int maxLength) {
        if (StrUtil.isBlank(text)) {
            return "";
        }

        // 清理HTML实体
        text = HtmlUtil.unescape(text);

        // 按句子分割，保持可读性
        String[] sentences = text.split("[。！？.!?]+");
        StrBuilder result = new StrBuilder();

        for (String sentence : sentences) {
            sentence = StrUtil.trim(sentence);
            if (StrUtil.isNotBlank(sentence)) {
                result.append(sentence).append("。");
                if (result.length() > maxLength) {
                    break;
                }
            }
        }

        return result.toString();
    }

    /**
     * 清理文本
     */
    private String cleanText(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }

        // 使用Hutool的工具清理
        text = HtmlUtil.cleanHtmlTag(text);
        text = HtmlUtil.unescape(text);
        text = StrUtil.trim(text);

        // 移除多余空白
        text = ReUtil.replaceAll(text, "\\s+", " ");
        text = ReUtil.replaceAll(text, "[\r\n]+", " ");

        return text;
    }

    /**
     * 清理最终结果
     */
    private String cleanFinalResult(String result) {
        // 移除多余空行
        result = ReUtil.replaceAll(result, "\n{4,}", "\n\n\n");

        // 移除每行末尾的空格
        String[] lines = result.split("\n");
        StrBuilder cleaned = new StrBuilder();
        for (String line : lines) {
            cleaned.append(StrUtil.trimEnd(line)).append("\n");
        }

        return cleaned.toString().trim();
    }

    /**
     * 智能截断文本
     */
    private String smartTruncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        // 寻找合适的截断点
        int cutPoint = maxLength;

        // 优先在段落结束处截断
        int lastParagraph = text.lastIndexOf("\n\n", cutPoint);
        if (lastParagraph > maxLength * 0.8) {
            cutPoint = lastParagraph;
        } else {
            // 其次在句子结束处截断
            String[] sentenceEnds = {"。", "！", "？", ". ", "! ", "? ", "\n"};
            for (String end : sentenceEnds) {
                int lastEnd = text.lastIndexOf(end, cutPoint);
                if (lastEnd > maxLength * 0.9) {
                    cutPoint = lastEnd + end.length();
                    break;
                }
            }
        }

        return StrUtil.format("{}\n\n... (内容已截断，共{}字符，已显示{}字符)",
                text.substring(0, cutPoint), text.length(), cutPoint);
    }
}
