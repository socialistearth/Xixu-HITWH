package edu.hitwh.fieldnote;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

final class ScheduleMath {
    static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Shanghai");
    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);
    static long millis(String date,String time) {
        return LocalDate.parse(date).atTime(LocalTime.parse(time,CLOCK)).atZone(SCHOOL_ZONE).toInstant().toEpochMilli();
    }
    static long alarmTime(String date,String start,int advance,long now) {
        if(advance<0||advance>1440)throw new IllegalArgumentException("提前分钟应在 0–1440 之间");
        long at=millis(date,start)-advance*60_000L;
        if(at<=now+5000)throw new IllegalArgumentException("提醒时间已过或距离现在不足 5 秒");
        return at;
    }
    static LocalDate classDate(String monday,int week,int day) {
        LocalDate d=LocalDate.parse(monday);
        if(d.getDayOfWeek()!=DayOfWeek.MONDAY||week<1||week>40||day<1||day>7)throw new IllegalArgumentException("学期或周次无效");
        return d.plusDays((week-1L)*7+day-1);
    }
}
