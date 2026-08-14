package com.ntropy.ai.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.ai.exception.AiReportErrorCode;
import com.ntropy.common.dto.ai.AiReportDetailSummary;
import com.ntropy.common.exception.ServiceException;

/** AI 리포트 상세 표시 모델을 한글 텍스트·표 중심 PDF로 생성한다. */
@Service
public class AiReportPdfService {

    private static final String FONT_RESOURCE = "/fonts/NotoSansKR-VF.ttf";
    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int TABLE_TEXT_MAX_CODE_POINTS = 60;

    public byte[] generate(AiReportDetailSummary report) {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream(FONT_RESOURCE)) {
            if (fontStream == null) {
                throw new IOException("PDF font resource is missing");
            }
            PDType0Font font = PDType0Font.load(document, fontStream);
            PdfWriter writer = new PdfWriter(document, font);
            writeReport(writer, report);
            writer.finish();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException(AiReportErrorCode.PDF_GENERATION_FAILED, exception);
        }
    }

    private void writeReport(PdfWriter writer, AiReportDetailSummary report) throws IOException {
        writer.title("Ntropy AI 월간 재무 리포트");
        writer.keyValue("대상 연월", report.yearMonth());
        writer.keyValue("리포트 ID", text(report.reportId()));
        writer.keyValue("생성 시각", report.createdAt() == null ? "-" : CREATED_AT.format(report.createdAt()));

        JsonNode financial = objectOrEmpty(report.financialSummary());
        writer.section("월간 재무 요약");
        writer.table(
                List.of("총소득", "총소비", "가용자금"),
                List.of(List.of(
                        won(financial.path("totalIncome")),
                        won(financial.path("totalExpense")),
                        won(financial.path("availableFunds"))
                )),
                new float[] {1, 1, 1}
        );
        writer.keyValue("전월 대비 소득 변화율", percent(financial.path("incomeChangeRate")));
        writer.keyValue("전월 대비 소비 변화율", percent(financial.path("expenseChangeRate")));

        writeCategories(writer, financial.path("topCategories"));
        writeJobs(writer, financial.path("jobSummaries"));
        writeRemainingFields(writer, financial,
                Set.of("totalIncome", "totalExpense", "availableFunds", "incomeChangeRate",
                        "expenseChangeRate", "topCategories", "jobSummaries"));

        JsonNode recommendation = objectOrEmpty(report.recommendation());
        writer.section("AI 재무 분석");
        writer.keyValue("재무 유형", scalar(recommendation.path("financialType")));
        writer.paragraph("재무활동 인사이트", scalar(recommendation.path("financialActivityInsight")));
        writer.paragraph("잡별 소득 인사이트", scalar(recommendation.path("jobInsight")));
        writer.paragraph("향후 소득 전망", scalar(recommendation.path("futureIncomeTrend")));

        writeRecommendedProduct(writer, recommendation.path("recommendedProduct"));
        writer.paragraph("추천 이유", scalar(recommendation.path("reasoning")));
        String benefitLabel = benefitLabel(recommendation.path("recommendedProduct").path("productType"));
        writer.keyValue(benefitLabel, won(recommendation.path("simulatedExtraIncome")));
        writeRemainingFields(writer, recommendation,
                Set.of("financialType", "financialActivityInsight", "jobInsight", "futureIncomeTrend",
                        "recommendedProduct", "reasoning", "simulatedExtraIncome"));

        writer.note("월 예상 혜택은 현재 재무 데이터와 상품 조건을 기준으로 계산한 단순 추정값이며, "
                + "실제 혜택은 납입 기간, 세금, 우대조건, 이용 실적 및 금융사 정책에 따라 달라질 수 있습니다.");
    }

    private void writeCategories(PdfWriter writer, JsonNode categories) throws IOException {
        writer.section("주요 소비 카테고리");
        List<List<String>> rows = new ArrayList<>();
        List<String> fullNames = new ArrayList<>();
        if (categories.isArray()) {
            for (JsonNode category : categories) {
                String displayName = scalar(category.path("displayName"));
                if (displayName.equals("-") && "AGGREGATED_OTHER".equals(category.path("category").asText())) {
                    displayName = "기타";
                }
                String tableName = abbreviateForTable(displayName);
                if (!tableName.equals(displayName)) {
                    fullNames.add(displayName);
                }
                rows.add(List.of(tableName, won(category.path("amount")), percent(category.path("ratio"))));
            }
        }
        writer.table(List.of("카테고리", "소비 금액", "소비 비율"), rows, new float[] {1.4f, 1, 0.8f});
        for (String fullName : fullNames) {
            writer.paragraph("전체 카테고리명", fullName);
        }
    }

    private void writeJobs(PdfWriter writer, JsonNode jobs) throws IOException {
        writer.section("잡별 소득 요약");
        List<List<String>> rows = new ArrayList<>();
        List<String> fullNames = new ArrayList<>();
        if (jobs.isArray()) {
            for (JsonNode job : jobs) {
                String jobName = scalar(job.path("jobName"));
                String tableName = abbreviateForTable(jobName);
                if (!tableName.equals(jobName)) {
                    fullNames.add(jobName);
                }
                rows.add(List.of(
                        tableName,
                        won(job.path("incomeAmount")),
                        percent(job.path("incomeRatio")),
                        workTime(job.path("totalWorkMinutes"))
                ));
            }
        }
        writer.table(List.of("잡 이름", "소득", "소득 비율", "근무시간"), rows,
                new float[] {1.2f, 1, 0.8f, 1});
        for (String fullName : fullNames) {
            writer.paragraph("전체 잡 이름", fullName);
        }
    }

    private void writeRecommendedProduct(PdfWriter writer, JsonNode product) throws IOException {
        writer.section("추천 금융상품");
        writer.keyValue("상품 ID", scalar(product.path("productId")));
        writer.keyValue("상품명", scalar(product.path("productName")));
        writer.keyValue("금융사", scalar(product.path("provider")));
        writer.keyValue("상품 유형", productTypeName(product.path("productType").asText("")));
        writer.paragraph("상품 요약", scalar(product.path("summary")));
        writer.paragraph("추천 대상", scalar(product.path("targetGroup")));
        writer.paragraph("N잡 인사이트", scalar(product.path("njobTrendTip")));
        writer.subsection("상품 상세 조건");
        writeDynamicNode(writer, product.path("details"), "상세");
        writeRemainingFields(writer, product,
                Set.of("productId", "productName", "provider", "productType", "summary", "targetGroup",
                        "njobTrendTip", "details"));
    }

    private void writeRemainingFields(PdfWriter writer, JsonNode object, Set<String> handled) throws IOException {
        if (!object.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!handled.contains(field.getKey())) {
                writeDynamicNode(writer, field.getValue(), humanize(field.getKey()));
            }
        }
    }

    private void writeDynamicNode(PdfWriter writer, JsonNode node, String label) throws IOException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            writer.keyValue(label, "-");
        } else if (node.isObject()) {
            if (!"상세".equals(label)) {
                writer.subsection(label);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            if (!fields.hasNext()) {
                writer.keyValue(label, "-");
                return;
            }
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                writeDynamicNode(writer, field.getValue(), humanize(field.getKey()));
            }
        } else if (node.isArray()) {
            if (node.size() == 0) {
                writer.keyValue(label, "-");
            } else {
                writer.subsection(label);
                int index = 1;
                for (JsonNode child : node) {
                    writeDynamicNode(writer, child, "항목 " + index++);
                }
            }
        } else {
            writer.keyValue(label, scalar(node));
        }
    }

    private static JsonNode objectOrEmpty(JsonNode node) {
        return node == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : node;
    }

    private static String won(JsonNode node) {
        return node != null && node.isNumber()
                ? java.text.NumberFormat.getIntegerInstance(Locale.KOREA).format(node.asLong()) + "원"
                : "-";
    }

    private static String abbreviateForTable(String value) {
        if (value == null || value.codePointCount(0, value.length()) <= TABLE_TEXT_MAX_CODE_POINTS) {
            return value;
        }
        int end = value.offsetByCodePoints(0, TABLE_TEXT_MAX_CODE_POINTS);
        return value.substring(0, end) + "…";
    }

    private static String percent(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return "-";
        }
        double value = node.asDouble() * 100;
        return String.format(Locale.KOREA, "%+.1f%%", value);
    }

    private static String workTime(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return "-";
        }
        long minutes = Math.max(0, node.asLong());
        long hours = minutes / 60;
        long remainder = minutes % 60;
        if (hours == 0) return remainder + "분";
        return remainder == 0 ? hours + "시간" : hours + "시간 " + remainder + "분";
    }

    private static String benefitLabel(JsonNode productType) {
        String type = productType.asText("").toUpperCase(Locale.ROOT);
        if (type.contains("SAVING") || type.contains("DEPOSIT")) return "월 예상 이자";
        if (type.contains("CARD")) return "월 예상 절감액";
        return "월 예상 혜택";
    }

    private static String productTypeName(String type) {
        if (type == null || type.isBlank()) return "-";
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CARD" -> "카드";
            case "SAVINGS", "SAVING", "DEPOSIT" -> "적금";
            default -> type;
        };
    }

    private static String scalar(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) return "-";
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private static String text(Object value) {
        return value == null ? "-" : value.toString();
    }

    private static String humanize(String key) {
        if (key == null || key.isBlank()) return "상세";
        String spaced = key.replace('_', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** PDFBox의 저수준 API 위에 줄바꿈·표·페이지 넘김을 제공하는 작은 레이아웃 계층. */
    static final class PdfWriter {
        private static final float MARGIN = 48;
        private static final float BOTTOM = 48;
        private static final float BODY_SIZE = 10;
        private static final float LINE_HEIGHT = 15;
        private static final PDColor BRAND = new PDColor(new float[] {0.16f, 0.35f, 0.72f}, PDDeviceRGB.INSTANCE);
        private static final PDColor LIGHT = new PDColor(new float[] {0.92f, 0.95f, 1f}, PDDeviceRGB.INSTANCE);

        private final PDDocument document;
        private final PDType0Font font;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        PdfWriter(PDDocument document, PDType0Font font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        void title(String value) throws IOException {
            ensure(42);
            text(value, MARGIN, y, 20, BRAND);
            y -= 34;
        }

        void section(String value) throws IOException {
            ensure(38);
            y -= 8;
            stream.setNonStrokingColor(BRAND);
            stream.addRect(MARGIN, y - 20, page.getMediaBox().getWidth() - MARGIN * 2, 25);
            stream.fill();
            text(value, MARGIN + 8, y - 13, 13, new PDColor(new float[] {1, 1, 1}, PDDeviceRGB.INSTANCE));
            y -= 32;
        }

        void subsection(String value) throws IOException {
            ensure(28);
            y -= 5;
            text(value, MARGIN, y, 11, BRAND);
            y -= 19;
        }

        void keyValue(String key, String value) throws IOException {
            paragraph(key, value);
        }

        void paragraph(String label, String value) throws IOException {
            ensure(LINE_HEIGHT * 2);
            text(label, MARGIN, y, 10, BRAND);
            y -= LINE_HEIGHT;
            for (String line : wrap(value, page.getMediaBox().getWidth() - MARGIN * 2, BODY_SIZE)) {
                ensure(LINE_HEIGHT);
                text(line, MARGIN, y, BODY_SIZE, null);
                y -= LINE_HEIGHT;
            }
            y -= 4;
        }

        void note(String value) throws IOException {
            ensure(35);
            y -= 8;
            for (String line : wrap("※ " + value, page.getMediaBox().getWidth() - MARGIN * 2, 8)) {
                ensure(12);
                text(line, MARGIN, y, 8, null);
                y -= 12;
            }
        }

        void table(List<String> headers, List<List<String>> rows, float[] weights) throws IOException {
            float width = page.getMediaBox().getWidth() - MARGIN * 2;
            float totalWeight = 0;
            for (float weight : weights) totalWeight += weight;
            float[] columnWidths = new float[weights.length];
            for (int i = 0; i < weights.length; i++) columnWidths[i] = width * weights[i] / totalWeight;

            tableRow(headers, columnWidths, true);
            if (rows.isEmpty()) {
                tableRow(List.of("데이터 없음"), new float[] {width}, false);
            } else {
                for (List<String> row : rows) tableRow(row, columnWidths, false);
            }
            y -= 8;
        }

        private void tableRow(List<String> cells, float[] widths, boolean header) throws IOException {
            List<List<String>> wrapped = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < cells.size(); i++) {
                float cellWidth = widths[Math.min(i, widths.length - 1)] - 10;
                List<String> lines = wrap(cells.get(i), cellWidth, 9);
                wrapped.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }
            float height = maxLines * 13 + 10;
            ensure(height);

            float x = MARGIN;
            for (int i = 0; i < wrapped.size(); i++) {
                float cellWidth = widths[Math.min(i, widths.length - 1)];
                if (header) {
                    stream.setNonStrokingColor(LIGHT);
                    stream.addRect(x, y - height + 4, cellWidth, height);
                    stream.fill();
                }
                stream.setStrokingColor(0.71f, 0.75f, 0.80f);
                stream.addRect(x, y - height + 4, cellWidth, height);
                stream.stroke();
                float lineY = y - 10;
                for (String line : wrapped.get(i)) {
                    text(line, x + 5, lineY, 9, header ? BRAND : null);
                    lineY -= 13;
                }
                x += cellWidth;
            }
            y -= height;
        }

        private List<String> wrap(String raw, float maxWidth, float size) throws IOException {
            String value = raw == null || raw.isBlank() ? "-" : raw.replace('\n', ' ').replace('\r', ' ');
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                String candidate = line + character;
                if (line.length() > 0 && stringWidth(candidate, size) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(character);
                offset += Character.charCount(codePoint);
            }
            if (line.length() > 0) lines.add(line.toString());
            if (lines.isEmpty()) lines.add("-");
            return lines;
        }

        private float stringWidth(String value, float size) throws IOException {
            return font.getStringWidth(value) / 1000f * size;
        }

        private void text(String value, float x, float baseline, float size, PDColor color) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color == null
                    ? new PDColor(new float[] {0.12f, 0.14f, 0.18f}, PDDeviceRGB.INSTANCE)
                    : color);
            stream.newLineAtOffset(x, baseline);
            stream.showText(value == null ? "-" : value);
            stream.endText();
        }

        private void ensure(float required) throws IOException {
            if (y - required < BOTTOM) newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                addPageNumber();
                stream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
            pageNumber++;
        }

        private void addPageNumber() throws IOException {
            String value = "- " + pageNumber + " -";
            float x = (page.getMediaBox().getWidth() - stringWidth(value, 8)) / 2;
            text(value, x, 24, 8, null);
        }

        void finish() throws IOException {
            addPageNumber();
            stream.close();
            stream = null;
        }
    }
}
