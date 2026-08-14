package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ntropy.common.dto.ai.AiReportDetailSummary;

class AiReportPdfServiceTest {

    @Test
    void includesFinancialJobsCategoriesRecommendationAndDynamicDetails() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AiReportDetailSummary report = new AiReportDetailSummary(
                31L,
                "2026-05",
                mapper.readTree("{"
                        + "\"totalIncome\":4000000,\"totalExpense\":2500000,\"availableFunds\":1500000,"
                        + "\"incomeChangeRate\":0.041,\"expenseChangeRate\":-0.03,"
                        + "\"topCategories\":[{\"category\":\"AGGREGATED_OTHER\",\"displayName\":\"기타\",\"amount\":500000,\"ratio\":0.2}],"
                        + "\"jobSummaries\":[{\"jobId\":1,\"jobName\":\"배달\",\"incomeAmount\":1000000,\"incomeRatio\":0.25,\"totalWorkMinutes\":1650}]}"),
                mapper.readTree("{"
                        + "\"financialType\":\"BALANCED\",\"financialActivityInsight\":\"지출 흐름이 안정적입니다.\","
                        + "\"jobInsight\":\"배달 소득이 꾸준합니다.\",\"futureIncomeTrend\":\"소득 증가가 예상됩니다.\","
                        + "\"reasoning\":\"소비 패턴에 적합합니다.\",\"simulatedExtraIncome\":23000,"
                        + "\"recommendedProduct\":{\"productId\":\"card-1\",\"productName\":\"생활 카드\","
                        + "\"provider\":\"가상은행\",\"productType\":\"CARD\",\"summary\":\"생활비 절감\","
                        + "\"targetGroup\":\"N잡 사용자\",\"njobTrendTip\":\"업무별 카드를 분리하세요.\","
                        + "\"details\":{\"minimumSpend\":300000,\"benefits\":[\"교통\",\"통신\"],\"nested\":{\"rate\":0.1}}}}"),
                LocalDateTime.of(2026, 6, 1, 9, 30)
        );

        byte[] pdf = new AiReportPdfService().generate(report);

        assertTrue(pdf.length > 1000);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("총소득"));
            assertTrue(text.contains("4,000,000원"));
            assertTrue(text.contains("27시간 30분"));
            assertTrue(text.contains("기타"));
            assertTrue(text.contains("생활 카드"));
            assertTrue(text.contains("Minimum Spend"));
            assertTrue(text.contains("교통"));
            assertTrue(text.contains("월 예상 절감액"));
            assertTrue(text.contains("단순 추정값"));
        }
    }

    @Test
    void wrapsLongKoreanTextAcrossPagesWithoutLosingTheTail() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String longInsight = "긴 한글 문장도 페이지 경계에서 안전하게 이어져야 합니다. ".repeat(180)
                + "마지막 안내 문장";
        AiReportDetailSummary report = new AiReportDetailSummary(
                32L, "2026-06", mapper.readTree("{}"),
                mapper.createObjectNode().put("financialActivityInsight", longInsight),
                LocalDateTime.of(2026, 7, 1, 0, 0)
        );

        byte[] pdf = new AiReportPdfService().generate(report);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            assertTrue(new PDFTextStripper().getText(document).contains("마지막 안내 문장"));
        }
    }

    @Test
    void preservesLongTableNamesAndDynamicDetailValuesOutsideTheBoundedTableCells() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String longCategoryName = "매우 긴 소비 카테고리 이름 ".repeat(80) + "카테고리 원문 끝";
        String longJobName = "매우 긴 잡 이름 ".repeat(100) + "잡 이름 원문 끝";
        String longDetail = "상품 상세 조건의 긴 설명입니다. ".repeat(180) + "상품 상세 원문 끝";

        ObjectNode financial = mapper.createObjectNode();
        ArrayNode categories = financial.putArray("topCategories");
        categories.addObject()
                .put("displayName", longCategoryName)
                .put("amount", 10000)
                .put("ratio", 1.0);
        ArrayNode jobs = financial.putArray("jobSummaries");
        jobs.addObject()
                .put("jobName", longJobName)
                .put("incomeAmount", 10000)
                .put("incomeRatio", 1.0)
                .put("totalWorkMinutes", 60);

        ObjectNode recommendation = mapper.createObjectNode();
        recommendation.putObject("recommendedProduct")
                .put("productType", "CARD")
                .putObject("details")
                .put("longDescription", longDetail);
        AiReportDetailSummary report = new AiReportDetailSummary(
                33L, "2026-07", financial, recommendation, LocalDateTime.of(2026, 8, 1, 0, 0)
        );

        byte[] pdf = new AiReportPdfService().generate(report);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            String compactText = text.replaceAll("\\s+", "");
            assertTrue(document.getNumberOfPages() > 1);
            assertTrue(text.contains("전체 카테고리명"));
            assertTrue(compactText.contains("카테고리원문끝"));
            assertTrue(text.contains("전체 잡 이름"));
            assertTrue(compactText.contains("잡이름원문끝"));
            assertTrue(compactText.contains("상품상세원문끝"));
        }
    }

    @Test
    void generatesPdfConcurrentlyWithoutSharingNumberFormatState() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AiReportPdfService service = new AiReportPdfService();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<byte[]>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                long amount = 1_000_000L + index * 111_111L;
                ObjectNode financial = mapper.createObjectNode().put("totalIncome", amount);
                AiReportDetailSummary report = new AiReportDetailSummary(
                        (long) index, "2026-08", financial, mapper.createObjectNode(),
                        LocalDateTime.of(2026, 9, 1, 0, 0)
                );
                tasks.add(() -> service.generate(report));
            }

            List<Future<byte[]>> results = executor.invokeAll(tasks);

            for (Future<byte[]> result : results) {
                try (PDDocument document = Loader.loadPDF(result.get())) {
                    assertTrue(new PDFTextStripper().getText(document).contains("원"));
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
