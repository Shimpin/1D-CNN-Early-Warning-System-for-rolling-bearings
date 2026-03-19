package org.sxk.dcnn.earlywarning.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.sxk.dcnn.earlywarning.entity.PredictionRecord;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页统计：故障类型占比、近期预测次数、故障预警数量。优先从 Redis 取，无则查库并写入缓存。
 */
@Service
public class StatsService {

    private static final String CACHE_KEY_CHARTS = "stats:charts";
    private static final long CACHE_SECONDS = 300;

    private final PredictionRecordService recordService;
    private final StringRedisTemplate redisTemplate;

    public StatsService(PredictionRecordService recordService, StringRedisTemplate redisTemplate) {
        this.recordService = recordService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 返回 Echarts 所需数据：pie（故障类型占比）、line（近期预测次数）、bar（故障预警数量）
     */
    public Map<String, Object> getChartsData(Long userId) {
        String cacheKey = buildChartsCacheKey(userId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(cached, Map.class);
            } catch (Exception ignored) { }
        }

        List<PredictionRecord> records = userId != null ? recordService.listByUserId(userId) : recordService.listAll();

        // 故障类型占比（饼图）：正常 / 内圈故障 / 外圈故障 / 滚动体故障
        Map<String, Long> faultTypeCount = new LinkedHashMap<>();
        faultTypeCount.put("正常", 0L);
        faultTypeCount.put("内圈故障", 0L);
        faultTypeCount.put("外圈故障", 0L);
        faultTypeCount.put("滚动体故障", 0L);
        for (PredictionRecord r : records) {
            String name = r.getFaultName();
            if (name == null) continue;
            if (name.contains("正常")) faultTypeCount.put("正常", faultTypeCount.get("正常") + 1);
            else if (name.contains("内圈")) faultTypeCount.put("内圈故障", faultTypeCount.get("内圈故障") + 1);
            else if (name.contains("外圈")) faultTypeCount.put("外圈故障", faultTypeCount.get("外圈故障") + 1);
            else if (name.contains("滚动体")) faultTypeCount.put("滚动体故障", faultTypeCount.get("滚动体故障") + 1);
        }
        List<Map<String, Object>> pieData = new ArrayList<>();
        for (Map.Entry<String, Long> e : faultTypeCount.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", e.getKey());
            item.put("value", e.getValue());
            pieData.add(item);
        }

        // 近期预测次数（折线图）：最近 7 天每日预测次数
        Map<LocalDate, Long> dayCount = records.stream()
            .filter(r -> r.getCreatedAt() != null)
            .collect(Collectors.groupingBy(
                r -> r.getCreatedAt().toLocalDate(),
                Collectors.counting()
            ));
        List<String> lineDates = new ArrayList<>();
        List<Long> lineValues = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            lineDates.add(d.format(fmt));
            lineValues.add(dayCount.getOrDefault(d, 0L));
        }

        // 故障预警数量（柱状图）：按故障类型统计预警条数
        // 注意：这里统计的是所有记录，包括正常和故障
        Map<String, Long> barData = new LinkedHashMap<>();
        barData.put("正常", 0L);
        barData.put("内圈故障", 0L);
        barData.put("外圈故障", 0L);
        barData.put("滚动体故障", 0L);
        for (PredictionRecord r : records) {
            String name = r.getFaultName();
            if (name == null) continue;
            if (name.contains("正常")) barData.put("正常", barData.get("正常") + 1);
            else if (name.contains("内圈")) barData.put("内圈故障", barData.get("内圈故障") + 1);
            else if (name.contains("外圈")) barData.put("外圈故障", barData.get("外圈故障") + 1);
            else if (name.contains("滚动体")) barData.put("滚动体故障", barData.get("滚动体故障") + 1);
        }
        List<Map<String, Object>> barList = new ArrayList<>();
        for (Map.Entry<String, Long> e : barData.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", e.getKey());
            item.put("value", e.getValue());
            barList.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("pie", pieData);
        result.put("line", Map.of("dates", lineDates, "values", lineValues));
        result.put("bar", barList);

        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) { }

        return result;
    }

    public void evictChartsCache(Long userId) {
        try {
            redisTemplate.delete(buildChartsCacheKey(userId));
        } catch (Exception ignored) {
        }
    }

    private String buildChartsCacheKey(Long userId) {
        return CACHE_KEY_CHARTS + (userId != null ? ":" + userId : "");
    }
}
