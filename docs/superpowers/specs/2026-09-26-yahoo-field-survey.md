# Yahoo Finance field survey — evidence for the typed instrument model (2026-09-26)

Three live surveys run through the library's own authenticated client. Percentages are presence of a non-empty value.

## Survey 3 (wide): 282 live instruments — v7 quote and quoteSummary (23 modules)

```
=== v7: returned instruments per class, and quoteType mismatches
  EQUITY          n=175 types={'EQUITY': 173, 'NONE': 2}
  ETF             n= 38 types={'ETF': 38}
  MUTUALFUND      n= 15 types={'MUTUALFUND': 15}
  INDEX           n= 18 types={'INDEX': 18}
  CRYPTOCURRENCY  n= 12 types={'CRYPTOCURRENCY': 12}
  CURRENCY        n= 12 types={'CURRENCY': 12}
  FUTURE          n= 14 types={'FUTURE': 14}

=== v7 guaranteed (>=99%) per quoteType, and near-guaranteed (90-99%) — fields that are 100% in EVERY type listed first
  UNIVERSAL: averageDailyVolume10Day, averageDailyVolume3Month, cryptoTradeable, currency, customPriceAlertConfidence, esgPopulated, exchange, exchangeDataDelayedBy, exchangeTimezoneName, exchangeTimezoneShortName, fiftyDayAverage, fiftyDayAverageChange, fiftyDayAverageChangePercent, fiftyTwoWeekChangePercent, fiftyTwoWeekHigh, fiftyTwoWeekHighChange, fiftyTwoWeekHighChangePercent, fiftyTwoWeekLow, fiftyTwoWeekLowChange, fiftyTwoWeekLowChangePercent, fiftyTwoWeekRange, firstTradeDateMilliseconds, fullExchangeName, fulldayChange, fulldayChangePercent, fulldayPrice, gmtOffSetMilliseconds, hasPrePostMarketData, language, market, marketState, priceHint, quoteType, region, regularMarketChange, regularMarketChangePercent, regularMarketPreviousClose, regularMarketPrice, regularMarketTime, shortName, sourceInterval, symbol, tradeable, triggerable, twoHundredDayAverage, twoHundredDayAverageChange, twoHundredDayAverageChangePercent, typeDisp

  [EQUITY] n=173
   guaranteed: ask, bid, bookValue, earningsTimestampEnd, earningsTimestampStart, epsTrailingTwelveMonths, financialCurrency, impliedSharesOutstanding, isEarningsDateEstimate, longName, marketCap, messageBoardId, priceToBook, quoteSourceName, regularMarketDayHigh, regularMarketDayLow, regularMarketDayRange, regularMarketOpen, regularMarketVolume, sharesOutstanding, trailingAnnualDividendRate, trailingAnnualDividendYield
   near(90-99): askSize(98), averageAnalystRating(90), bidSize(98), earningsCallTimestampEnd(98), earningsCallTimestampStart(98), earningsTimestamp(97), epsCurrentYear(92), epsForward(99), forwardPE(99), priceEpsCurrentYear(92)
   partial(30-90): displayName(60), dividendDate(41), dividendRate(59), dividendYield(59), postMarketChange(72), postMarketChangePercent(72), postMarketPrice(72), postMarketTime(72), trailingPE(82)

  [ETF] n=38
   guaranteed: ask, askSize, bid, bidSize, longName, messageBoardId, quoteSourceName, regularMarketDayHigh, regularMarketDayLow, regularMarketDayRange, regularMarketOpen, regularMarketVolume
   near(90-99): 
   partial(30-90): bookValue(58), dividendYield(82), epsTrailingTwelveMonths(76), financialCurrency(58), netAssets(87), netExpenseRatio(89), postMarketChange(68), postMarketChangePercent(68), postMarketPrice(68), postMarketTime(68), priceToBook(58), sharesOutstanding(58), trailingAnnualDividendRate(71), trailingAnnualDividendYield(71), trailingPE(66), trailingThreeMonthNavReturns(82), trailingThreeMonthReturns(82), ytdReturn(82)

  [MUTUALFUND] n=15
   guaranteed: dividendRate, dividendYield, longName, messageBoardId, netAssets, netExpenseRatio, quoteSourceName, trailingThreeMonthReturns, ytdReturn
   near(90-99): 
   partial(30-90): bookValue(60), epsTrailingTwelveMonths(80), financialCurrency(60), priceToBook(60), sharesOutstanding(53), trailingAnnualDividendRate(67), trailingAnnualDividendYield(67), trailingPE(80)

  [INDEX] n=18
   guaranteed: ask, askSize, bid, bidSize, longName, messageBoardId, regularMarketDayHigh, regularMarketDayLow, regularMarketDayRange, regularMarketOpen, regularMarketVolume
   near(90-99): 
   partial(30-90): quoteSourceName(89)

  [CRYPTOCURRENCY] n=12
   guaranteed: circulatingSupply, coinImageUrl, coinMarketCapLink, fromCurrency, lastMarket, logoUrl, longName, marketCap, maxSupply, messageBoardId, quoteSourceName, regularMarketDayHigh, regularMarketDayLow, regularMarketDayRange, regularMarketOpen, regularMarketVolume, startDate, toCurrency, totalSupply, volume24Hr, volumeAllCurrencies
   near(90-99): 
   partial(30-90): 

  [CURRENCY] n=12
   guaranteed: ask, askSize, bid, bidSize, longName, messageBoardId, quoteSourceName, regularMarketDayHigh, regularMarketDayLow, regularMarketDayRange, regularMarketOpen, regularMarketVolume
   near(90-99): 
   partial(30-90): 

  [FUTURE] n=14
   guaranteed: ask, bid, contractSymbol, expireDate, expireIsoDate, headSymbolAsString, openInterest, quoteSourceName, regularMarketDayHigh, regularMarketDayLow, regularMarketDayRange, regularMarketOpen, regularMarketVolume, underlyingExchangeSymbol, underlyingSymbol
   near(90-99): 
   partial(30-90): askSize(86), bidSize(79)

=== quoteSummary status per class
  EQUITY          {200: 176, 404: 6} {'Quote not found for symbol: NKLA': 1, 'Quote not found for symbol: ROG.SW': 1, 'Quote not found for symbol: OS': 1, 'Quote not found for symbol: DBS.SI': 1, 'Quote not found for symbol: BITF': 1, 'Quote not found for symbol: ZK': 1}
  ETF             {200: 38} 
  MUTUALFUND      {200: 15} 
  INDEX           {200: 18} 
  CRYPTOCURRENCY  {200: 12} 
  CURRENCY        {200: 12} 
  FUTURE          {200: 14} 

=== quoteSummary module presence % (200 responses only)
  module                     EQUITY    ETF MUTUAL  INDEX CRYPTO CURREN FUTURE
  assetProfile                   98    100    100      0    100      0      0
  calendarEvents                 99      0      0      0      0      0      0
  defaultKeyStatistics           98    100    100    100      0      0      0
  earningsHistory                99      0      0      0      0      0      0
  earningsTrend                  99      0      0      0      0      0      0
  financialData                  99      0      0      0      0      0      0
  fundOwnership                  99      0      0      0      0      0      0
  fundPerformance                 0    100    100      0      0      0      0
  fundProfile                     0    100    100      0      0      0      0
  insiderHolders                 99      0      0      0      0      0      0
  insiderTransactions            80      0      0      0      0      0      0
  institutionOwnership           99      0      0      0      0      0      0
  majorHoldersBreakdown          98      0      0      0      0      0      0
  netSharePurchaseActivity       98      0      0      0      0      0      0
  price                         100    100    100    100    100    100    100
  quoteType                     100    100    100    100    100    100    100
  recommendationTrend            99      0      0      0      0      0      0
  secFilings                     69      0      0      0      0      0      0
  summaryDetail                  99    100    100    100    100    100    100
  summaryProfile                 98    100    100      0    100      0      0
  topHoldings                     0    100    100      0      0      0      0
  upgradeDowngradeHistory        73      0      0      0      0      0      0

=== EQUITY key presence within modules (n=176 with 200)

  --- price (module present in 176/176)
   guaranteed: maxAge, quoteType, symbol
   near: averageDailyVolume10Day(99), averageDailyVolume3Month(99), currency(99), currencySymbol(99), exchange(99), exchangeDataDelayedBy(99), exchangeName(99), fulldayChange(99), fulldayChangePercent(99), fulldayPrice(99), longName(98), marketCap(98), marketState(99), priceHint(99), quoteSourceName(94), regularMarketChange(99), regularMarketChangePercent(99), regularMarketDayHigh(99), regularMarketDayLow(99), regularMarketOpen(99), regularMarketPreviousClose(99), regularMarketPrice(99), regularMarketSource(99), regularMarketTime(99), regularMarketVolume(99), shortName(99)
   partial: postMarketChange(70), postMarketChangePercent(70), postMarketPrice(70), postMarketSource(71), postMarketTime(70), preMarketSource(73)

  --- summaryDetail (module present in 174/176)
   guaranteed: allTimeHigh, allTimeLow, ask, averageDailyVolume10Day, averageVolume, averageVolume10days, bid, currency, dayHigh, dayLow, fiftyDayAverage, fiftyTwoWeekHigh, fiftyTwoWeekLow, fulldayChange, fulldayChangePercent, fulldayPrice, marketCap, maxAge, nonDilutedMarketCap, open, previousClose, priceHint, regularMarketDayHigh, regularMarketDayLow, regularMarketOpen, regularMarketPreviousClose, regularMarketVolume, tradeable, trailingAnnualDividendRate, trailingAnnualDividendYield, twoHundredDayAverage, volume
   near: askSize(98), beta(98), bidSize(98), forwardPE(99), payoutRatio(99), priceToSalesTrailing12Months(99)
   partial: dividendRate(59), dividendYield(59), exDividendDate(61), fiveYearAvgDividendYield(57), trailingPE(81)

  --- defaultKeyStatistics (module present in 173/176)
   guaranteed: 52WeekChange, SandP52WeekChange, bookValue, enterpriseToRevenue, enterpriseValue, floatShares, heldPercentInsiders, heldPercentInstitutions, impliedSharesOutstanding, lastFiscalYearEnd, maxAge, mostRecentQuarter, netIncomeToCommon, nextFiscalYearEnd, priceHint, profitMargins, sharesOutstanding, trailingEps
   near: beta(99), enterpriseToEbitda(90), forwardEps(99), forwardPE(94), priceToBook(95)
   partial: dateShortInterest(75), earningsQuarterlyGrowth(79), lastDividendDate(62), lastDividendValue(62), lastSplitDate(64), lastSplitFactor(64), pegRatio(84), sharesPercentSharesOut(74), sharesShort(75), sharesShortPreviousMonthDate(75), sharesShortPriorMonth(75), shortPercentOfFloat(71), shortRatio(75)

  --- financialData (module present in 174/176)
   guaranteed: currentPrice, ebitdaMargins, financialCurrency, grossMargins, grossProfits, maxAge, operatingMargins, profitMargins, recommendationKey, revenuePerShare, totalCash, totalCashPerShare, totalDebt, totalRevenue
   near: currentRatio(90), numberOfAnalystOpinions(97), operatingCashflow(96), quickRatio(90), recommendationMean(90), returnOnAssets(99), returnOnEquity(94), revenueGrowth(98), targetHighPrice(97), targetLowPrice(97), targetMeanPrice(97), targetMedianPrice(97)
   partial: debtToEquity(85), earningsGrowth(76), ebitda(90), freeCashflow(88)

  --- assetProfile (module present in 173/176)
   guaranteed: address1, city, companyOfficers, country, industry, industryDisp, industryKey, longBusinessSummary, maxAge, sector, sectorDisp, sectorKey, website, zip
   near: compensationAsOfEpochDate(95), fullTimeEmployees(98), phone(99)
   partial: address2(35), auditRisk(77), boardRisk(77), compensationRisk(77), governanceEpochDate(77), irWebsite(37), overallRisk(77), shareHolderRightsRisk(77), state(66)

  --- calendarEvents (module present in 174/176)
   guaranteed: earnings, maxAge
   near: 
   partial: dividendDate(41), exDividendDate(61)

=== FUND (ETF+MUTUALFUND) key presence in fund modules
  [ETF] fundProfile (38/38) guaranteed: family, feesExpensesInvestment, feesExpensesInvestmentCat, legalType, managementInfo, maxAge | partial: categoryName(71), styleBoxUrl(84)
  [ETF] topHoldings (38/38) guaranteed: bondPosition, cashPosition, convertiblePosition, equityHoldings, maxAge, otherPosition, preferredPosition, stockPosition | partial: bondRatings(97), holdings(82), sectorWeightings(74)
  [ETF] fundPerformance (38/38) guaranteed: annualTotalReturns, maxAge, pastQuarterlyReturns, riskOverviewStatistics, riskOverviewStatisticsCat, trailingReturns, trailingReturnsCat, trailingReturnsNav | partial: fundCategoryName(71), performanceOverview(82), performanceOverviewCat(71)
  [ETF] defaultKeyStatistics (38/38) guaranteed: fundFamily, fundInceptionDate, legalType, maxAge, priceHint | partial: beta3Year(82), category(71), fiveYearAverageReturn(76), threeYearAverageReturn(79), totalAssets(87), yield(82), ytdReturn(82)
  [ETF] summaryDetail (38/38) guaranteed: allTimeHigh, allTimeLow, ask, askSize, averageDailyVolume10Day, averageVolume, averageVolume10days, bid, bidSize, currency, dayHigh, dayLow, fiftyDayAverage, fiftyTwoWeekHigh, fiftyTwoWeekLow, fulldayChange, fulldayChangePercent, fulldayPrice, maxAge, open, previousClose, priceHint, regularMarketDayHigh, regularMarketDayLow, regularMarketOpen, regularMarketPreviousClose, regularMarketVolume, tradeable, twoHundredDayAverage, volume | partial: navPrice(92), totalAssets(87), trailingAnnualDividendRate(71), trailingAnnualDividendYield(71), trailingPE(66), yield(82)
  [MUTUALFUND] fundProfile (15/15) guaranteed: brokerages, family, feesExpensesInvestment, feesExpensesInvestmentCat, initInvestment, managementInfo, maxAge, styleBoxUrl, subseqInvestment | partial: categoryName(67), subseqAipInvestment(33)
  [MUTUALFUND] topHoldings (15/15) guaranteed: bondPosition, bondRatings, cashPosition, convertiblePosition, equityHoldings, maxAge, otherPosition, preferredPosition, stockPosition | partial: bondHoldings(67), holdings(93), sectorWeightings(93)
  [MUTUALFUND] fundPerformance (15/15) guaranteed: annualTotalReturns, loadAdjustedReturns, maxAge, pastQuarterlyReturns, performanceOverview, rankInCategory, riskOverviewStatistics, riskOverviewStatisticsCat, trailingReturns, trailingReturnsCat, trailingReturnsNav | partial: fundCategoryName(67), performanceOverviewCat(67)
  [MUTUALFUND] defaultKeyStatistics (15/15) guaranteed: annualHoldingsTurnover, annualReportExpenseRatio, beta3Year, fundInceptionDate, lastCapGain, lastDividendValue, maxAge, morningStarOverallRating, morningStarRiskRating, priceHint, totalAssets | partial: 
  [MUTUALFUND] summaryDetail (15/15) guaranteed: allTimeHigh, allTimeLow, averageDailyVolume10Day, averageVolume, averageVolume10days, currency, fiftyDayAverage, fiftyTwoWeekHigh, fiftyTwoWeekLow, fulldayChange, fulldayChangePercent, fulldayPrice, maxAge, previousClose, priceHint, regularMarketPreviousClose, totalAssets, tradeable, twoHundredDayAverage, yield, ytdReturn | partial: trailingAnnualDividendRate(67), trailingAnnualDividendYield(67), trailingPE(80)
```

## Two-source union coverage on live instruments (the assembly table's basis)

```
live instruments: {'EQUITY': 173, 'ETF': 38, 'MUTUALFUND': 15, 'INDEX': 18, 'CRYPTOCURRENCY': 12, 'CURRENCY': 12, 'FUTURE': 14}
dead/unknown (excluded): ['RIDE', 'WISH']

=== UNION coverage % on live instruments (any source). Columns: class; cell = union% [v7-only%]
field                              EQUITY          ETF      MUTUALF        INDEX      CRYPTOC      CURRENC       FUTURE
shortName                       100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
longName                         99 [ 99]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]      0 [  0]
currency                        100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
exchange                        100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
timezone                        100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
marketState                     100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
price                           100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
change                          100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
changePercent                   100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
previousClose                   100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
priceTime                       100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
fiftyTwoWeekLow                 100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
fiftyTwoWeekHigh                100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
fiftyDayAverage                 100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
twoHundredDayAverage            100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
averageVolume10Day              100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
averageVolume3Month             100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
firstTradeDate                  100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
priceHint                       100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]    100 [100]
open                            100 [100]    100 [100]      0 [  0]    100 [100]    100 [100]    100 [100]    100 [100]
dayLow                          100 [100]    100 [100]      0 [  0]    100 [100]    100 [100]    100 [100]    100 [100]
dayHigh                         100 [100]    100 [100]      0 [  0]    100 [100]    100 [100]    100 [100]    100 [100]
volume                          100 [100]    100 [100]      0 [  0]    100 [100]    100 [100]    100 [100]    100 [100]
bid                              99 [ 99]    100 [100]      0 [  0]    100 [100]      0 [  0]    100 [100]    100 [100]
ask                              99 [ 99]    100 [100]      0 [  0]    100 [100]      0 [  0]    100 [100]    100 [100]
bidSize                          98 [ 98]    100 [100]      0 [  0]    100 [100]      0 [  0]    100 [100]     79 [ 79]
askSize                          98 [ 98]    100 [100]      0 [  0]    100 [100]      0 [  0]    100 [100]     86 [ 86]
postMarketPrice                  72 [ 72]     68 [ 68]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
marketCap                        99 [ 99]      0 [  0]      0 [  0]      0 [  0]    100 [100]      0 [  0]      0 [  0]
sharesOutstanding               100 [100]     58 [ 58]     53 [ 53]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
impliedSharesOutstanding         99 [ 99]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
floatShares                     100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
bookValue                        99 [ 99]     58 [ 58]     60 [ 60]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
priceToBook                      99 [ 99]     58 [ 58]     60 [ 60]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
trailingEps                      99 [ 99]     76 [ 76]     80 [ 80]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
forwardEps                       99 [ 99]      5 [  5]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
forwardPE                        99 [ 99]      5 [  5]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
trailingPE                       82 [ 82]     66 [ 66]     80 [ 80]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
beta                             99 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
financialCurrency               100 [100]     58 [ 58]     60 [ 60]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
trailingAnnualDividendRate       99 [ 99]     71 [ 71]     67 [ 67]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
trailingAnnualDividendYield      99 [ 99]     71 [ 71]     67 [ 67]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
dividendRate                     59 [ 59]      0 [  0]    100 [100]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
dividendYield                    59 [ 59]     82 [ 82]    100 [100]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
exDividendDate                   62 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
payoutRatio                      99 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
enterpriseValue                  99 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
pegRatio                         84 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
profitMargins                   100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
heldPercentInsiders             100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
heldPercentInstitutions         100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
lastFiscalYearEnd                99 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
mostRecentQuarter                99 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
nextEarningsDate                100 [100]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
averageAnalystRating             90 [ 90]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
recommendationMean               90 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
recommendationKey               100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
numberOfAnalystOpinions          97 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
targetMeanPrice                  97 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
totalRevenue                    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
grossMargins                    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
operatingMargins                100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
totalCash                       100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
totalDebt                       100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
debtToEquity                     86 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
freeCashflow                     88 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
operatingCashflow                97 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
ebitda                           90 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
returnOnEquity                   94 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
revenueGrowth                    99 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
sector                          100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
industry                        100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
website                         100 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [  0]      0 [  0]      0 [  0]
country                         100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
fullTimeEmployees                98 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
longBusinessSummary             100 [  0]     71 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
companyOfficers                 100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
netAssets                         0 [  0]     87 [ 87]    100 [100]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
expenseRatio                      0 [  0]     89 [ 89]    100 [100]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
ytdReturn                         0 [  0]    100 [ 82]    100 [100]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
threeMonthReturn                  0 [  0]    100 [ 82]    100 [100]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
oneYearReturn                     0 [  0]    100 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
threeYearReturn                   0 [  0]    100 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
fiveYearReturn                    0 [  0]    100 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
fundYield                        59 [ 59]     82 [ 82]    100 [100]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
fundFamily                        0 [  0]    100 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
legalType                         0 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
fundCategory                      0 [  0]     71 [  0]     67 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
inceptionDate                     0 [  0]    100 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
beta3Year                         0 [  0]     82 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
navPrice                          0 [  0]     92 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
holdings                          0 [  0]     82 [  0]     93 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
sectorWeightings                  0 [  0]     74 [  0]     93 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
stockPosition                     0 [  0]    100 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
bondPosition                      0 [  0]    100 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
cashPosition                      0 [  0]    100 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
annualTotalReturns                0 [  0]    100 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
morningstarOverallRating          0 [  0]      0 [  0]    100 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]
circulatingSupply                 0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]      0 [  0]      0 [  0]
totalSupply                       0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]      0 [  0]      0 [  0]
maxSupply                         0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]      0 [  0]      0 [  0]
volume24Hr                        0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]      0 [  0]      0 [  0]
fromCurrency                      0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]      0 [  0]      0 [  0]
toCurrency                        0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]      0 [  0]      0 [  0]
startDate                         0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]      0 [  0]      0 [  0]
contractSymbol                    0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]
expireDate                        0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]
openInterest                      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]
underlyingSymbol                  0 [  0]      0 [  0]      0 [  0]      6 [  6]      0 [  0]      0 [  0]    100 [100]
headSymbol                        0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]      0 [  0]    100 [100]

=== nested fund objects: inner key presence among ETF/MF that have the module
  ETF fundProfile.feesExpensesInvestment (38/38): annualHoldingsTurnover=100%, annualReportExpenseRatio=89%, totalNetAssets=100%
  MUTUALFUND fundProfile.feesExpensesInvestment (15/15): annualReportExpenseRatio=100%, grossExpRatio=100%, netExpRatio=100%, projectionValues=100%
  ETF fundPerformance.trailingReturns (38/38): asOfDate=100%, fiveYear=100%, lastBearMkt=100%, lastBullMkt=100%, oneMonth=100%, oneYear=100%, tenYear=100%, threeMonth=100%, threeYear=100%, ytd=100%
  MUTUALFUND fundPerformance.trailingReturns (15/15): asOfDate=100%, fiveYear=100%, lastBearMkt=100%, lastBullMkt=100%, oneMonth=100%, oneYear=100%, tenYear=100%, threeMonth=100%, threeYear=100%, ytd=100%
  ETF topHoldings.equityHoldings (38/38): priceToBook=100%, priceToCashflow=100%, priceToEarnings=100%, priceToSales=100%
  MUTUALFUND topHoldings.equityHoldings (15/15): medianMarketCap=100%, medianMarketCapCat=66%, priceToBook=100%, priceToBookCat=66%, priceToCashflow=100%, priceToCashflowCat=66%, priceToEarnings=100%, priceToEarningsCat=66%, priceToSales=100%, priceToSalesCat=66%, threeYearEarningsGrowth=100%, threeYearEarningsGrowthCat=66%
  ETF fundProfile.managementInfo (38/38): 
  MUTUALFUND fundProfile.managementInfo (15/15): managerBio=100%, managerName=40%, startdate=100%
```

## Survey 2: options / fundamentals timeseries / chart metadata per class

## Options / fundamentals / chart metadata per class (2026-09-26)

### CRYPTOCURRENCY BTC_USD
- options: 200 exp=0 calls=0 quoteKeys=70
- ts: 200 series={}
- chart: 200 instrumentType=CRYPTOCURRENCY bars=101 metaKeys=32 tradingPeriods=True currentTP=True events=[]
### CURRENCY EURUSD_X
- options: 200 exp=0 calls=0 quoteKeys=61
- ts: 200 series={}
- chart: 200 instrumentType=CURRENCY bars=120 metaKeys=32 tradingPeriods=True currentTP=True events=[]
### EQUITY AAPL
- options: 200 exp=23 calls=36 quoteKeys=90
- ts: 200 series={'annualTotalAssets': 4, 'annualTotalRevenue': 4, 'annualFreeCashFlow': 4, 'annualNetIncome': 4, 'quarterlyTotalRevenue': 5, 'trailingTotalRevenue': 2}
- chart: 200 instrumentType=EQUITY bars=36 metaKeys=32 tradingPeriods=True currentTP=True events=[]
### EQUITY PLUG
- options: 200 exp=11 calls=9 quoteKeys=86
- ts: 200 series={'annualFreeCashFlow': 4, 'quarterlyTotalRevenue': 5, 'annualTotalRevenue': 4, 'annualTotalAssets': 4, 'trailingTotalRevenue': 1, 'annualNetIncome': 4}
- chart: 200 instrumentType=EQUITY bars=36 metaKeys=32 tradingPeriods=True currentTP=True events=[]
### EQUITY SAP_DE
- options: 200 exp=0 calls=0 quoteKeys=84
- ts: 200 series={'annualFreeCashFlow': 4, 'annualTotalAssets': 4, 'trailingTotalRevenue': 2, 'annualNetIncome': 4, 'annualTotalRevenue': 4, 'quarterlyTotalRevenue': 5}
- chart: 200 instrumentType=EQUITY bars=46 metaKeys=32 tradingPeriods=True currentTP=True events=[]
### ETF GLD
- options: 200 exp=25 calls=63 quoteKeys=78
- ts: 200 series={}
- chart: 200 instrumentType=ETF bars=36 metaKeys=32 tradingPeriods=True currentTP=True events=[]
### ETF SPY
- options: 200 exp=28 calls=120 quoteKeys=79
- ts: 200 series={}
- chart: 200 instrumentType=ETF bars=36 metaKeys=32 tradingPeriods=True currentTP=True events=[]
### FUTURE ES_F
- options: 200 exp=0 calls=0 quoteKeys=66
- ts: 200 series={}
- chart: 200 instrumentType=FUTURE bars=114 metaKeys=31 tradingPeriods=True currentTP=True events=[]
### FUTURE GC_F
- options: 200 exp=0 calls=0 quoteKeys=66
- ts: 200 series={}
- chart: 200 instrumentType=FUTURE bars=114 metaKeys=31 tradingPeriods=True currentTP=True events=[]
### INDEX _GSPC
- options: 200 exp=0 calls=0 quoteKeys=61
- ts: 200 series={}
- chart: 200 instrumentType=INDEX bars=36 metaKeys=32 tradingPeriods=True currentTP=True events=[]
### INDEX _SPX
- options: 200 exp=54 calls=170 quoteKeys=62
- ts: 200 series={}
- chart: 200 instrumentType=INDEX bars=36 metaKeys=32 tradingPeriods=True currentTP=True events=[]
### INDEX _VIX
- options: 200 exp=11 calls=44 quoteKeys=60
- ts: 200 series={}
- chart: 200 instrumentType=INDEX bars=71 metaKeys=32 tradingPeriods=True currentTP=True events=[]
### MUTUALFUND FCNTX
- options: 200 exp=0 calls=0 quoteKeys=58
- ts: 200 series={}
- chart: 200 instrumentType=MUTUALFUND bars=5 metaKeys=26 tradingPeriods=False currentTP=True events=[]
### MUTUALFUND VFIAX
- options: 200 exp=0 calls=0 quoteKeys=65
- ts: 200 series={}
- chart: 200 instrumentType=MUTUALFUND bars=5 metaKeys=26 tradingPeriods=False currentTP=True events=[]

### chart meta key presence (count of instruments having key, of 14)

chartPreviousClose=14, currency=14, currentTradingPeriod=14, dataGranularity=14, exchangeName=14, exchangeTimezoneName=14, fiftyTwoWeekHigh=14, fiftyTwoWeekLow=14, firstTradeDate=14, fullExchangeName=14, fulldayChange=14, fulldayChangePercent=14, fulldayPrice=14, gmtoffset=14, hasPrePostMarketData=14, instrumentType=14, longName=12, previousClose=12, priceHint=14, range=14, regularMarketChangePercent=14, regularMarketDayHigh=12, regularMarketDayLow=12, regularMarketPrice=14, regularMarketTime=14, regularMarketVolume=12, scale=12, shortName=14, symbol=14, timezone=14, tradingPeriods=12, validRanges=14
