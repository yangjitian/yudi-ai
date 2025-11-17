package com.yudi.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.DayOfWeek;

/**
 * 日期时间工具类（提供获取当前日期和时间的功能）
 */
@Component
public class DateTimeTool {


    @Tool(name = "getCurrentDateTime", description = "Get the current date and time. Optional format parameter: 'date' for date only, 'time' for time only, 'datetime' for date and time, 'full' for complete information including weekday. Default is 'full'.")
    public String getCurrentDateTime(
            @ToolParam(description = "Optional format: 'date', 'time', 'datetime', or 'full'. Default is 'full'.") String format) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        if (format == null || format.isEmpty()) {
            format = "full";
        }

        return switch (format.toLowerCase()) {
            case "date" -> date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "time" -> time.format(DateTimeFormatter.ISO_LOCAL_TIME);
            case "datetime" -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            case "full" -> {
                DayOfWeek dayOfWeek = date.getDayOfWeek();
                String dayOfWeekStr = switch (dayOfWeek) {
                    case MONDAY -> "星期一";
                    case TUESDAY -> "星期二";
                    case WEDNESDAY -> "星期三";
                    case THURSDAY -> "星期四";
                    case FRIDAY -> "星期五";
                    case SATURDAY -> "星期六";
                    case SUNDAY -> "星期日";
                };
                yield String.format("当前日期：%s (%s)\n当前时间：%s",
                        date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        dayOfWeekStr,
                        time.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
            default -> {
                // 如果格式不匹配，返回完整信息
                DayOfWeek dayOfWeek = date.getDayOfWeek();
                String dayOfWeekStr = switch (dayOfWeek) {
                    case MONDAY -> "星期一";
                    case TUESDAY -> "星期二";
                    case WEDNESDAY -> "星期三";
                    case THURSDAY -> "星期四";
                    case FRIDAY -> "星期五";
                    case SATURDAY -> "星期六";
                    case SUNDAY -> "星期日";
                };
                yield String.format("当前日期：%s (%s)\n当前时间：%s",
                        date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        dayOfWeekStr,
                        time.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
        };
    }

    /**
     * 获取当前日期
     */
    @Tool(name = "getCurrentDate", description = "Get the current date in yyyy-MM-dd format")
    public String getCurrentDate() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * 获取当前时间
     */
    @Tool(name = "getCurrentTime", description = "Get the current time in HH:mm:ss format")
    public String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
    }
}

