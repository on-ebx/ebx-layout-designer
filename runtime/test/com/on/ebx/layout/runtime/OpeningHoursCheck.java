package com.on.ebx.layout.runtime;
import java.time.*; import java.util.*;
/** Checks of the opening hours block's readers (days, times, dates, closed flag, slots after midnight). */
public class OpeningHoursCheck { static int f=0; static void eq(Object a, Object b, String m){ if(!Objects.equals(a,b)){ f++; System.out.println("FAIL "+m+": "+a+" != "+b);} }
 public static void main(String[] x){
  Map<String,Integer> none=new HashMap<>(); Map<String,Integer> codes=new HashMap<>(); codes.put("D1",1); codes.put("D7",7);
  eq(OpeningHours.isoDay("MON",none),1,"MON"); eq(OpeningHours.isoDay("sunday",none),7,"sunday"); eq(OpeningHours.isoDay("Lun.",none),1,"Lun."); eq(OpeningHours.isoDay("LUNEDÌ",none),1,"lunedi");
  eq(OpeningHours.isoDay(3,none),3,"3"); eq(OpeningHours.isoDay("8",none),null,"8"); eq(OpeningHours.isoDay("d7",codes),7,"codes"); eq(OpeningHours.isoDay("MON",codes),null,"codes only");
  eq(OpeningHours.time("10:30"),LocalTime.of(10,30),"10:30"); eq(OpeningHours.time("9:05"),LocalTime.of(9,5),"9:05"); eq(OpeningHours.time("19:00:00"),LocalTime.of(19,0),"secs"); eq(OpeningHours.time("10h30"),LocalTime.of(10,30),"10h30");
  eq(OpeningHours.time("25:00"),null,"25"); eq(OpeningHours.time("abc"),null,"abc"); eq(OpeningHours.time(LocalTime.NOON),LocalTime.NOON,"LocalTime");
  Calendar c=Calendar.getInstance(); c.clear(); c.set(1970,0,1,8,15); eq(OpeningHours.time(c.getTime()),LocalTime.of(8,15),"Date time");
  eq(OpeningHours.date("2026-12-24"),LocalDate.of(2026,12,24),"iso"); c.clear(); c.set(2026,11,25); eq(OpeningHours.date(c.getTime()),LocalDate.of(2026,12,25),"Date");
  eq(OpeningHours.isTrue(Boolean.TRUE),true,"bool"); eq(OpeningHours.isTrue("oui"),true,"oui"); eq(OpeningHours.isTrue(null),false,"null");
  OpeningHours.Slot n=new OpeningHours.Slot(LocalTime.of(22,0),LocalTime.of(2,0)); eq(n.contains(LocalTime.of(23,0)),true,"night 23"); eq(n.contains(LocalTime.of(1,0)),true,"night 1"); eq(n.contains(LocalTime.of(12,0)),false,"night 12");
  eq(n.toString(),"22:00 – 02:00","toString");
  System.out.println(f==0?"PASS: opening hours parsing":"FAILED "+f); System.exit(f==0?0:1);}}
