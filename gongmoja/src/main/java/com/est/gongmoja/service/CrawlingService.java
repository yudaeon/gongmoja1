package com.est.gongmoja.service;

import com.est.gongmoja.entity.StockEntity;
import com.est.gongmoja.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CrawlingService {
    private final StockRepository repository;

    @PostConstruct
    public void getCrawlData() throws IOException {

        int shareAmount = 0;
        String stockName = "", industry = "", sponsor="";
        LocalDateTime refundDate = null, ipoDate = null;
        String year = "2023"; // 연도
        String url = "http://www.ipostock.co.kr/sub03/ipo04.asp?str1=2023&str2=7"; // 대상 웹 페이지 URL, 어디까지 크롤링할 지 정해야함.
        // TODO 월별 크롤링 http://www.ipostock.co.kr/sub03/ipo04.asp?str1={year}&str2={month}

        try {
            Document document = Jsoup.connect(url).get();
            Elements oddData = document.select("tr[bgcolor=#f8fafc]:contains(원)"); // 홀수 라인 종목
            Elements evenData = document.select("tr[bgcolor=#ffffff]:contains(원)"); // 짝수 라인 종목
            Elements comb = new Elements();
            comb.addAll(oddData);
            comb.addAll(evenData);

            for (Element element : comb) { // element : 한 종목 칼럼 데이터
                Elements columns = element.select("td");
                List<String> strings = new ArrayList<>();
                for (Element column : columns) {
                    strings.add(column.text());
                }
                strings.remove(0); // [0] 공백 데이터 삭제

                // 청약일정 parsing (ex. "08.29 ~ 08.30" -> 2023-08-10T10:00, 2023-08-11T16:00)
                if (strings.get(0).equals("공모철회"))
                    continue; // TODO 로직 추가
                String[] dates = strings.get(0).split("~");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd.HH.mm.ss");
                LocalDateTime startDate = LocalDateTime.parse(year + "." + dates[0].substring(0, dates[0].length() - 1) + ".10.00.00", formatter);
                LocalDateTime endDate = LocalDateTime.parse(year + "." + dates[1].substring(1) + ".16.00.00", formatter);

                // 환불일
                if (!strings.get(5).equals("")) {
                    refundDate = LocalDateTime.parse(year + "." + strings.get(5).substring(0, dates[0].length() - 1) + ".09.00.00", formatter);
                }

                // 상장일
                if (!strings.get(6).equals(""))
                {
                    ipoDate = LocalDateTime.parse(year + "." + strings.get(6).substring(0, dates[0].length() - 1) + ".09.00.00", formatter);
                }

                // 공모가
                String priceStr = strings.get(3);
                String priceStrParse = priceStr.replaceAll("[^0-9]", "");
                int price = Integer.parseInt(priceStrParse);

                // 경쟁률
                String competitionRate = strings.get(7);

                // 종목별 세부링크 내에서 크롤링
                Element linkElement = element.select("a[href]").first();
                if (linkElement != null) {
                    String link = linkElement.attr("href"); // 종목 고유링크
                    String detailUrl = "http://www.ipostock.co.kr" + link;
                    Document detailDoc = Jsoup.connect(detailUrl).get();

                    // 공모주 이름
                    Element nameElem = detailDoc.select("strong.view_tit").first();
                    stockName = nameElem.text();

                    // 분야
                    Element industryElem = detailDoc.select("td:has(font[color=213894])").last();
                    industry = industryElem.ownText().substring(1);

                    // 주간사
                    Elements sponsorElems = detailDoc.select("table.view_tb");
                    String sponsorList =sponsorElems.select("td > strong:contains(증권), td > strong:contains(투자)").text();
                    sponsor = sponsorList.replace("(", "");

                    // 주식 총발행량
                    // TODO 주간사별 발행량 or 모든 주간사 합계 발행량
                    Elements shareAmountElem = detailDoc.select("table.view_tb");
                    Element detailShareAmountElem = shareAmountElem.select("td[bgcolor=#FFFFFF]:contains(주)").first();
                    String shareAmountStr = detailShareAmountElem.text().replaceAll("[^0-9]", "");
                    shareAmount = Integer.parseInt(shareAmountStr);
                }

                // entity build
                StockEntity stockEntity = StockEntity.builder()
                        .startDate(startDate)
                        .endDate(endDate)
                        .name(stockName)
                        .industry(industry)
                        .shareAmount(shareAmount)
                        .price(price)
                        .competitionRate(competitionRate) // 실시간 경쟁률 아님 (기관 경쟁률)
                        .sponsor(sponsor)
                        .ipoDate(ipoDate)
                        .refundDate(refundDate)
                        .build();

                repository.save(stockEntity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
