package edu.hitwh.fieldnote;
public class SchoolTermTest {public static void main(String[] args){String[][] cases={{"2026–2027 秋季","2026秋季"},{"2026–2027 春季","2027春季"},{"2027春季","2027春季"},{"示例学期",""}};for(String[] c:cases)if(!SchoolPages.token(c[0]).equals(c[1]))throw new AssertionError(c[0]+" => "+SchoolPages.token(c[0]));System.out.println("Native semester selection: 4 cases passed");}}
