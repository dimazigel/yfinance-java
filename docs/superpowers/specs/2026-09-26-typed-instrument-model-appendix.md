# Appendix A — Field tables (Intrinsic rule)

Kinds: **R** required → non-null component (missing ⇒ downgrade / `Skipped(MODULE_ABSENT)`); **O** optional → `Optional<T>`; **C:name** member of optional cluster `name` (present only if every member is; `(R)` marks a required cluster); **L** list → empty when Yahoo omits.
Coverage = % of live instruments in the wide survey with the value in at least one listed source. Sources: `v7:` = `/v7/finance/quote` field; `qs:` = `/v10/finance/quoteSummary` `module.key`.
Unit rules: `changePercent`, v7 `dividendYield`, `fiveYearAvgDividendYield`, `debtToEquity` are PERCENTS on the wire → stored as fractions; all other yields/margins/held-percent are already fractions; epoch seconds/millis → `Instant`; date-only epochs → `LocalDate`. Any field whose unit is marked *verify at impl* must be confirmed against a fixture before the mapper is written.

### Universal core (all classes) — coverage shown is the minimum across classes

| field | kind | type | sources (precedence) | coverage | notes |
|---|---|---|---|---|---|
| `symbol` | R | `Symbol` | `v7:symbol` → `qs:quoteType.symbol` | 100% |  |
| `shortName` | R | `String` | `v7:shortName` → `qs:quoteType.shortName` → `qs:price.shortName` | 100% |  |
| `longName` | O | `String` | `v7:longName` → `qs:quoteType.longName` → `qs:price.longName` | 0% | futures never; 1 equity lacked |
| `currency` | R | `Currency` | `v7:currency` → `qs:price.currency` → `qs:summaryDetail.currency` | 100% | non-null; pence codes such as `GBp` are not ISO — see design §10 (planned `QuoteCurrency` value type) |
| `exchange` | R | `String` | `v7:exchange` → `qs:quoteType.exchange` → `qs:price.exchange` | 100% | code, e.g. NMS |
| `fullExchangeName` | R | `String` | `v7:fullExchangeName` → `qs:price.exchangeName` | 100% |  |
| `exchangeTimezone` | R | `ZoneId` | `v7:exchangeTimezoneName` → `qs:quoteType.timeZoneFullName` | 100% |  |
| `marketState` | R | `MarketState` | `v7:marketState` → `qs:price.marketState` | 100% | enum; unknown→OTHER |
| `price` | R | `BigDecimal` | `v7:regularMarketPrice` → `qs:price.regularMarketPrice` | 100% |  |
| `change` | R | `BigDecimal` | `v7:regularMarketChange` → `qs:price.regularMarketChange` | 100% |  |
| `changePercent` | R | `BigDecimal` | `v7:regularMarketChangePercent` → `qs:price.regularMarketChangePercent` | 100% | PERCENT in both sources → fraction (÷100) |
| `previousClose` | R | `BigDecimal` | `v7:regularMarketPreviousClose` → `qs:price.regularMarketPreviousClose` → `qs:summaryDetail.previousClose` | 100% |  |
| `priceTime` | R | `Instant` | `v7:regularMarketTime` → `qs:price.regularMarketTime` | 100% | epoch seconds |
| `fiftyTwoWeekLow` | R | `BigDecimal` | `v7:fiftyTwoWeekLow` → `qs:summaryDetail.fiftyTwoWeekLow` | 100% |  |
| `fiftyTwoWeekHigh` | R | `BigDecimal` | `v7:fiftyTwoWeekHigh` → `qs:summaryDetail.fiftyTwoWeekHigh` | 100% |  |
| `fiftyDayAverage` | R | `BigDecimal` | `v7:fiftyDayAverage` → `qs:summaryDetail.fiftyDayAverage` | 100% |  |
| `twoHundredDayAverage` | R | `BigDecimal` | `v7:twoHundredDayAverage` → `qs:summaryDetail.twoHundredDayAverage` | 100% |  |
| `averageVolume10Day` | R | `long` | `v7:averageDailyVolume10Day` → `qs:summaryDetail.averageDailyVolume10Day` → `qs:price.averageDailyVolume10Day` | 100% |  |
| `averageVolume3Month` | R | `long` | `v7:averageDailyVolume3Month` → `qs:summaryDetail.averageVolume` → `qs:price.averageDailyVolume3Month` | 100% |  |
| `firstTradeDate` | R | `Instant` | `v7:firstTradeDateMilliseconds` | 100% | epoch millis |
| `priceHint` | R | `int` | `v7:priceHint` → `qs:price.priceHint` | 100% |  |
| `hasPrePostMarketData` | R | `boolean` | `v7:hasPrePostMarketData` | 100% |  |

### Session (all classes except MutualFund)

| field | kind | type | sources (precedence) | coverage | notes |
|---|---|---|---|---|---|
| `open` | R | `BigDecimal` | `v7:regularMarketOpen` → `qs:price.regularMarketOpen` → `qs:summaryDetail.open` | 100% |  |
| `dayLow` | R | `BigDecimal` | `v7:regularMarketDayLow` → `qs:price.regularMarketDayLow` → `qs:summaryDetail.dayLow` | 100% |  |
| `dayHigh` | R | `BigDecimal` | `v7:regularMarketDayHigh` → `qs:price.regularMarketDayHigh` → `qs:summaryDetail.dayHigh` | 100% |  |
| `volume` | R | `long` | `v7:regularMarketVolume` → `qs:price.regularMarketVolume` → `qs:summaryDetail.volume` | 100% |  |

### TopOfBook cluster (Equity, Etf, Index, FxPair, Future)

| field | kind | type | sources (precedence) | coverage | notes |
|---|---|---|---|---|---|
| `bid` | C:book | `BigDecimal` | `v7:bid` → `qs:summaryDetail.bid` | 99% |  |
| `ask` | C:book | `BigDecimal` | `v7:ask` → `qs:summaryDetail.ask` | 99% |  |
| `bidSize` | O | `long` | `v7:bidSize` → `qs:summaryDetail.bidSize` | 98% | inside book, Optional |
| `askSize` | O | `long` | `v7:askSize` → `qs:summaryDetail.askSize` | 98% | inside book, Optional |

### Equity — snapshot

| field | kind | type | sources (precedence) | coverage | notes |
|---|---|---|---|---|---|
| `marketCap` | R | `BigDecimal` | `v7:marketCap` → `qs:price.marketCap` → `qs:summaryDetail.marketCap` | 99% | only miss: BAC-PL (preferred) → downgrade |
| `sharesOutstanding` | R | `long` | `v7:sharesOutstanding` → `qs:defaultKeyStatistics.sharesOutstanding` | 100% |  |
| `impliedSharesOutstanding` | R | `long` | `v7:impliedSharesOutstanding` → `qs:defaultKeyStatistics.impliedSharesOutstanding` | 99% | only miss: BAC-PL |
| `financialCurrency` | R | `Currency` | `v7:financialCurrency` → `qs:financialData.financialCurrency` | 100% |  |
| `nextEarnings.expected` | C:nextEarnings(R) | `Instant` | `v7:earningsTimestamp` → `v7:earningsTimestampStart` → `qs:calendarEvents.earnings.earningsDate.0` | 100% | cluster is REQUIRED as a whole |
| `nextEarnings.windowStart` | C:nextEarnings(R) | `Instant` | `v7:earningsTimestampStart` | 100% |  |
| `nextEarnings.windowEnd` | C:nextEarnings(R) | `Instant` | `v7:earningsTimestampEnd` | 100% |  |
| `nextEarnings.isEstimate` | C:nextEarnings(R) | `boolean` | `v7:isEarningsDateEstimate` | 100% |  |
| `bookValue` | O | `BigDecimal` | `v7:bookValue` → `qs:defaultKeyStatistics.bookValue` | 99% | miss: 005930.KS (Samsung) → Optional (Intrinsic) |
| `priceToBook` | O | `BigDecimal` | `v7:priceToBook` → `qs:defaultKeyStatistics.priceToBook` | 99% | Samsung |
| `trailingEps` | O | `BigDecimal` | `v7:epsTrailingTwelveMonths` → `qs:defaultKeyStatistics.trailingEps` | 99% | Samsung |
| `forwardEps` | O | `BigDecimal` | `v7:epsForward` → `qs:defaultKeyStatistics.forwardEps` | 99% | Samsung, BAC-PL |
| `forwardPE` | O | `BigDecimal` | `v7:forwardPE` → `qs:summaryDetail.forwardPE` → `qs:defaultKeyStatistics.forwardPE` | 99% |  |
| `trailingPE` | O | `BigDecimal` | `v7:trailingPE` → `qs:summaryDetail.trailingPE` | 82% | loss-makers |
| `trailingDividend.rate` | C:trailingDividend | `BigDecimal` | `v7:trailingAnnualDividendRate` → `qs:summaryDetail.trailingAnnualDividendRate` | 99% | Samsung |
| `trailingDividend.yield` | C:trailingDividend | `BigDecimal` | `v7:trailingAnnualDividendYield` → `qs:summaryDetail.trailingAnnualDividendYield` | 99% | fraction in both |
| `currentDividend.rate` | C:currentDividend | `BigDecimal` | `v7:dividendRate` → `qs:summaryDetail.dividendRate` | 59% |  |
| `currentDividend.yield` | C:currentDividend | `BigDecimal` | `v7:dividendYield` → `qs:summaryDetail.dividendYield` | 59% | v7 PERCENT, summaryDetail fraction → fraction |
| `currentYearEps.eps` | C:currentYearEps | `BigDecimal` | `v7:epsCurrentYear` | 92% |  |
| `currentYearEps.priceToEps` | C:currentYearEps | `BigDecimal` | `v7:priceEpsCurrentYear` | 92% |  |
| `averageAnalystRating` | O | `String` | `v7:averageAnalystRating` | 90% | display string, e.g. '2.2 - Buy' |
| `postMarketPrice` | C:postMarket | `BigDecimal` | `v7:postMarketPrice` → `qs:price.postMarketPrice` | 72% |  |
| `postMarketChange` | C:postMarket | `BigDecimal` | `v7:postMarketChange` → `qs:price.postMarketChange` | 72% |  |
| `postMarketChangePercent` | C:postMarket | `BigDecimal` | `v7:postMarketChangePercent` → `qs:price.postMarketChangePercent` | 72% | percent→fraction |
| `postMarketTime` | C:postMarket | `Instant` | `v7:postMarketTime` → `qs:price.postMarketTime` | 72% | epoch s |

### Equity — detail

| field | kind | type | sources (precedence) | coverage | notes |
|---|---|---|---|---|---|
| `profile.sector` | R | `String` | `qs:assetProfile.sector` → `qs:summaryProfile.sector` | 100% |  |
| `profile.industry` | R | `String` | `qs:assetProfile.industry` → `qs:summaryProfile.industry` | 100% |  |
| `profile.country` | R | `String` | `qs:assetProfile.country` → `qs:summaryProfile.country` | 100% |  |
| `profile.city` | R | `String` | `qs:assetProfile.city` | 100% |  |
| `profile.address1` | R | `String` | `qs:assetProfile.address1` | 100% |  |
| `profile.zip` | R | `String` | `qs:assetProfile.zip` | 99% |  |
| `profile.website` | R | `URI` | `qs:assetProfile.website` → `qs:summaryProfile.website` | 100% | lenient URI |
| `profile.longBusinessSummary` | R | `String` | `qs:assetProfile.longBusinessSummary` → `qs:summaryProfile.longBusinessSummary` | 100% |  |
| `profile.officers` | R | `List<Officer>` | `qs:assetProfile.companyOfficers` | 100% | may be empty list |
| `profile.fullTimeEmployees` | O | `int` | `qs:assetProfile.fullTimeEmployees` | 98% | COST, AZO, 1299.HK |
| `profile.phone` | O | `String` | `qs:assetProfile.phone` | 99% |  |
| `profile.state` | O | `String` | `qs:assetProfile.state` | 66% |  |
| `profile.irWebsite` | O | `URI` | `qs:assetProfile.irWebsite` | 37% |  |
| `profile.governance.*` | C:governance | `int×5` | `qs:assetProfile.auditRisk` → `qs:assetProfile.boardRisk` → `qs:assetProfile.compensationRisk` → `qs:assetProfile.shareHolderRightsRisk` → `qs:assetProfile.overallRisk` | 77% | perfect cluster 77.5% |
| `statistics.floatShares` | R | `long` | `qs:defaultKeyStatistics.floatShares` | 100% |  |
| `statistics.heldPercentInsiders` | R | `BigDecimal` | `qs:defaultKeyStatistics.heldPercentInsiders` | 100% | fraction |
| `statistics.heldPercentInstitutions` | R | `BigDecimal` | `qs:defaultKeyStatistics.heldPercentInstitutions` | 100% | fraction |
| `statistics.profitMargins` | R | `BigDecimal` | `qs:defaultKeyStatistics.profitMargins` → `qs:financialData.profitMargins` | 100% | fraction |
| `statistics.beta` | O | `BigDecimal` | `qs:summaryDetail.beta` → `qs:defaultKeyStatistics.beta` | 99% | miss: COST, WKHS |
| `statistics.enterpriseValue` | O | `BigDecimal` | `qs:defaultKeyStatistics.enterpriseValue` | 99% | BNP.PA |
| `statistics.enterpriseToRevenue` | O | `BigDecimal` | `qs:defaultKeyStatistics.enterpriseToRevenue` | 99% |  |
| `statistics.enterpriseToEbitda` | O | `BigDecimal` | `qs:defaultKeyStatistics.enterpriseToEbitda` | 90% | 90% |
| `statistics.fiscal.lastFiscalYearEnd` | C:fiscal | `LocalDate` | `qs:defaultKeyStatistics.lastFiscalYearEnd` | 99% | BNP.PA; epoch s → LocalDate |
| `statistics.fiscal.nextFiscalYearEnd` | C:fiscal | `LocalDate` | `qs:defaultKeyStatistics.nextFiscalYearEnd` | 99% |  |
| `statistics.fiscal.mostRecentQuarter` | C:fiscal | `LocalDate` | `qs:defaultKeyStatistics.mostRecentQuarter` | 99% |  |
| `statistics.pegRatio` | O | `BigDecimal` | `qs:defaultKeyStatistics.pegRatio` | 84% | 84% |
| `statistics.payoutRatio` | O | `BigDecimal` | `qs:summaryDetail.payoutRatio` | 99% | fraction |
| `statistics.priceToSales` | O | `BigDecimal` | `qs:summaryDetail.priceToSalesTrailing12Months` | 99% |  |
| `statistics.earningsQuarterlyGrowth` | O | `BigDecimal` | `qs:defaultKeyStatistics.earningsQuarterlyGrowth` | 79% | 79% |
| `statistics.shortInterest.*` | C:shortInterest | `…` | `qs:defaultKeyStatistics.sharesShort` → `qs:defaultKeyStatistics.shortRatio` → `qs:defaultKeyStatistics.dateShortInterest` → `qs:defaultKeyStatistics.sharesShortPriorMonth` → `qs:defaultKeyStatistics.sharesPercentSharesOut` | 75% | 74.6%; shortPercentOfFloat Optional inside (70.5%) |
| `statistics.lastSplit.*` | C:lastSplit | `LocalDate+String` | `qs:defaultKeyStatistics.lastSplitDate` → `qs:defaultKeyStatistics.lastSplitFactor` | 64% | perfect 63.6% |
| `statistics.lastDividend.*` | C:lastDividend | `BigDecimal+LocalDate` | `qs:defaultKeyStatistics.lastDividendValue` → `qs:defaultKeyStatistics.lastDividendDate` | 62% | 62.4% |
| `statistics.exDividendDate` | O | `LocalDate` | `qs:summaryDetail.exDividendDate` → `qs:calendarEvents.exDividendDate` | 62% | 61.8% |
| `statistics.fiveYearAvgDividendYield` | O | `BigDecimal` | `qs:summaryDetail.fiveYearAvgDividendYield` | 57% | 57%; PERCENT → fraction |
| `financials.currentPrice` | R | `BigDecimal` | `qs:financialData.currentPrice` | 100% |  |
| `financials.totalRevenue` | R | `BigDecimal` | `qs:financialData.totalRevenue` | 100% |  |
| `financials.revenuePerShare` | R | `BigDecimal` | `qs:financialData.revenuePerShare` | 100% |  |
| `financials.grossProfits` | R | `BigDecimal` | `qs:financialData.grossProfits` | 100% |  |
| `financials.margins.gross` | R | `BigDecimal` | `qs:financialData.grossMargins` | 100% | fraction |
| `financials.margins.operating` | R | `BigDecimal` | `qs:financialData.operatingMargins` | 100% |  |
| `financials.margins.ebitda` | R | `BigDecimal` | `qs:financialData.ebitdaMargins` | 100% |  |
| `financials.totalCash` | R | `BigDecimal` | `qs:financialData.totalCash` | 100% |  |
| `financials.totalCashPerShare` | R | `BigDecimal` | `qs:financialData.totalCashPerShare` | 100% |  |
| `financials.totalDebt` | R | `BigDecimal` | `qs:financialData.totalDebt` | 100% |  |
| `financials.revenueGrowth` | O | `BigDecimal` | `qs:financialData.revenueGrowth` | 99% | NNE, VOD.L |
| `financials.debtToEquity` | O | `BigDecimal` | `qs:financialData.debtToEquity` | 86% | 86%; PERCENT |
| `financials.ebitda` | O | `BigDecimal` | `qs:financialData.ebitda` | 90% | 90% |
| `financials.freeCashflow` | O | `BigDecimal` | `qs:financialData.freeCashflow` | 88% | 88% |
| `financials.operatingCashflow` | O | `BigDecimal` | `qs:financialData.operatingCashflow` | 97% | 97% |
| `financials.returnOnEquity` | O | `BigDecimal` | `qs:financialData.returnOnEquity` | 94% | 94% |
| `financials.returnOnAssets` | O | `BigDecimal` | `qs:financialData.returnOnAssets` | 99% | 99% |
| `financials.liquidity.*` | C:liquidity | `BigDecimal×2` | `qs:financialData.currentRatio` → `qs:financialData.quickRatio` | 91% | perfect 90.8% |
| `financials.earningsGrowth` | O | `BigDecimal` | `qs:financialData.earningsGrowth` | 77% | 76% |
| `analysts.recommendationKey` | R | `String` | `qs:financialData.recommendationKey` | 100% | buy/hold/… vocabulary |
| `analysts.targets.*` | C:targets | `BigDecimal×4 + int` | `qs:financialData.targetLowPrice` → `qs:financialData.targetMeanPrice` → `qs:financialData.targetMedianPrice` → `qs:financialData.targetHighPrice` → `qs:financialData.numberOfAnalystOpinions` | 97% | perfect 96.5% |
| `analysts.rating.*` | C:rating | `BigDecimal + String` | `qs:financialData.recommendationMean` → `v7:averageAnalystRating` | 90% | 90.2% |
| `analysts.recommendationTrend` | L | `List<RecommendationPeriod>` | `qs:recommendationTrend.trend` | 98% | list |
| `analysts.earningsHistory` | L | `List<…>` | `qs:earningsHistory.history` | 92% |  |
| `analysts.estimates` | L | `List<…>` | `qs:earningsTrend.trend` | 100% | estimates/epsTrend/epsRevisions/growth from one module |
| `analysts.upgradesDowngrades` | L | `List<…>` | `qs:upgradeDowngradeHistory.history` | 74% | module 73% → empty list |
| `analysts.secFilings` | L | `List<SecFiling>` | `qs:secFilings.filings` | 69% | module 69% → empty |
| `ownership.breakdown.*` | R | `BigDecimal×3 + int` | `qs:majorHoldersBreakdown.insidersPercentHeld` → `qs:majorHoldersBreakdown.institutionsPercentHeld` → `qs:majorHoldersBreakdown.institutionsFloatPercentHeld` → `qs:majorHoldersBreakdown.institutionsCount` | 100% | 98% module |
| `ownership.institutions` | L | `List<InstitutionalHolder>` | `qs:institutionOwnership.ownershipList` | 96% |  |
| `ownership.funds` | L | `List<InstitutionalHolder>` | `qs:fundOwnership.ownershipList` | 96% |  |
| `ownership.insiders` | L | `List<InsiderHolder>` | `qs:insiderHolders.holders` | 79% |  |
| `ownership.insiderTransactions` | L | `List<InsiderTransaction>` | `qs:insiderTransactions.transactions` | 79% | module 80% → empty |
| `ownership.netSharePurchaseActivity` | R | `NetSharePurchaseActivity` | `qs:netSharePurchaseActivity.period` | 100% | 98% module |

### Etf — snapshot (+ Session, TopOfBook)

| field | kind | type | sources (precedence) | coverage | notes |
|---|---|---|---|---|---|
| `netAssets` | O | `BigDecimal` | `v7:netAssets` → `qs:defaultKeyStatistics.totalAssets` → `qs:summaryDetail.totalAssets` | 87% | 87%: UCITS lack |
| `expenseRatio` | O | `BigDecimal` | `v7:netExpenseRatio` → `qs:fundProfile.feesExpensesInvestment.annualReportExpenseRatio` | 89% | 89%: UCITS lack; unit *verify at impl* (v7 vs fundProfile) |
| `yield` | O | `BigDecimal` | `v7:dividendYield` → `qs:summaryDetail.yield` → `qs:defaultKeyStatistics.yield` | 82% | 82% |
| `navPrice` | O | `BigDecimal` | `qs:summaryDetail.navPrice` | 92% | 92% |
| `beta3Year` | O | `BigDecimal` | `qs:defaultKeyStatistics.beta3Year` | 82% | 82% |
| `ytdReturn` | R | `BigDecimal` | `v7:ytdReturn` → `qs:defaultKeyStatistics.ytdReturn` → `qs:fundPerformance.trailingReturns.ytd` | 100% | 100% via fundPerformance |
| `threeMonthReturn` | R | `BigDecimal` | `v7:trailingThreeMonthReturns` → `qs:fundPerformance.trailingReturns.threeMonth` | 100% | 100% |
| `trailingThreeMonthNavReturns` | O | `BigDecimal` | `v7:trailingThreeMonthNavReturns` | 82% | 82% |
| `legalType` | R | `String` | `qs:fundProfile.legalType` → `qs:defaultKeyStatistics.legalType` | 100% | 100% ETF (detail) |
| `equityLikeStats.*` | C:equityLikeStats | `BigDecimal×2 + long + Currency` | `v7:bookValue` → `v7:priceToBook` → `v7:sharesOutstanding` → `v7:financialCurrency` | 58% | perfect 58% |
| `trailingPE` | O | `BigDecimal` | `v7:trailingPE` → `qs:summaryDetail.trailingPE` | 66% | 66% |
| `trailingDividend.*` | C:trailingDividend | `BigDecimal×2` | `v7:trailingAnnualDividendRate` → `v7:trailingAnnualDividendYield` | 71% | 71% |
| `postMarketPrice` | C:postMarket | `BigDecimal` | `v7:postMarketPrice` → `qs:price.postMarketPrice` | 68% |  |
| `postMarketChange` | C:postMarket | `BigDecimal` | `v7:postMarketChange` → `qs:price.postMarketChange` | 68% |  |
| `postMarketChangePercent` | C:postMarket | `BigDecimal` | `v7:postMarketChangePercent` → `qs:price.postMarketChangePercent` | 68% | percent→fraction |
| `postMarketTime` | C:postMarket | `Instant` | `v7:postMarketTime` → `qs:price.postMarketTime` | 68% | epoch s |

### Fund detail shared by Etf and MutualFund (coverage shown for ETF)

| field | kind | type | sources (precedence) | coverage | notes |
|---|---|---|---|---|---|
| `family` | R | `String` | `qs:fundProfile.family` → `qs:defaultKeyStatistics.fundFamily` | 100% |  |
| `inceptionDate` | R | `LocalDate` | `qs:defaultKeyStatistics.fundInceptionDate` | 100% | epoch s |
| `trailingReturns.{ytd,oneMonth,threeMonth,oneYear,threeYear,fiveYear,tenYear,asOf}` | R | `BigDecimal×7 + LocalDate` | `qs:fundPerformance.trailingReturns.ytd` | 100% | fractions; 100% both classes |
| `annualTotalReturns` | R | `List<YearReturn>` | `qs:fundPerformance.annualTotalReturns` | 100% |  |
| `allocation.{stock,bond,cash,preferred,convertible,other}` | R | `BigDecimal×6` | `qs:topHoldings.stockPosition` | 100% | fractions; 100% |
| `equityValuation.{priceToEarnings,priceToBook,priceToSales,priceToCashflow}` | R | `BigDecimal×4` | `qs:topHoldings.equityHoldings.priceToEarnings` | 100% | 100% |
| `holdings` | L | `List<Holding>` | `qs:topHoldings.holdings` | 82% | empty for metal/crypto ETFs (82% ETF, 93% MF) |
| `sectorWeightings` | L | `List<SectorWeight>` | `qs:topHoldings.sectorWeightings` | 74% | 74% ETF |
| `bondRatings` | L | `List<BondRating>` | `qs:topHoldings.bondRatings` | 97% | 97% ETF |
| `category` | O | `String` | `qs:fundProfile.categoryName` → `qs:defaultKeyStatistics.category` → `qs:fundPerformance.fundCategoryName` | 71% | 71% ETF / 67% MF |
| `longBusinessSummary` | O | `String` | `qs:assetProfile.longBusinessSummary` | 71% | 71% ETF; 100% MF → MF: R |

### MutualFund — snapshot and detail (no Session, no TopOfBook)

| field | kind | type | sources (precedence) | coverage | notes |
|---|---|---|---|---|---|
| `netAssets` | R | `BigDecimal` | `v7:netAssets` → `qs:defaultKeyStatistics.totalAssets` → `qs:summaryDetail.totalAssets` | 100% | US sample n=15 |
| `expenseRatio` | R | `BigDecimal` | `v7:netExpenseRatio` → `qs:fundProfile.feesExpensesInvestment.annualReportExpenseRatio` → `qs:defaultKeyStatistics.annualReportExpenseRatio` | 100% |  |
| `yield` | R | `BigDecimal` | `v7:dividendYield` → `qs:summaryDetail.yield` | 100% | v7 PERCENT → fraction |
| `dividendRate` | R | `BigDecimal` | `v7:dividendRate` | 100% |  |
| `ytdReturn` | R | `BigDecimal` | `v7:ytdReturn` → `qs:summaryDetail.ytdReturn` → `qs:fundPerformance.trailingReturns.ytd` | 100% |  |
| `threeMonthReturn` | R | `BigDecimal` | `v7:trailingThreeMonthReturns` → `qs:fundPerformance.trailingReturns.threeMonth` | 100% |  |
| `morningstar.{overall,risk}` | R | `int×2` | `qs:defaultKeyStatistics.morningStarOverallRating` → `qs:defaultKeyStatistics.morningStarRiskRating` | 100% | detail |
| `annualHoldingsTurnover` | R | `BigDecimal` | `qs:defaultKeyStatistics.annualHoldingsTurnover` | 100% | detail |
| `lastCapGain` | R | `BigDecimal` | `qs:defaultKeyStatistics.lastCapGain` | 100% | detail |
| `lastDividendValue` | R | `BigDecimal` | `qs:defaultKeyStatistics.lastDividendValue` | 100% | detail |
| `beta3Year` | R | `BigDecimal` | `qs:defaultKeyStatistics.beta3Year` | 100% | detail |
| `minimums.{initial,subsequent}` | R | `BigDecimal×2` | `qs:fundProfile.initInvestment` → `qs:fundProfile.subseqInvestment` | 100% | detail |
| `brokerages` | R | `List<String>` | `qs:fundProfile.brokerages` | 100% | detail |
| `loadAdjustedReturns` | R | `LoadAdjustedReturns` | `qs:fundPerformance.loadAdjustedReturns` | 100% | detail |
| `rankInCategory` | R | `RankInCategory` | `qs:fundPerformance.rankInCategory` | 100% | detail |
| `styleBoxUrl` | R | `URI` | `qs:fundProfile.styleBoxUrl` | 100% | detail |
| `equityLikeStats.*` | C:equityLikeStats | `…` | `v7:bookValue` → `v7:priceToBook` → `v7:sharesOutstanding` → `v7:financialCurrency` | 60% | 60% |
| `trailingPE` | O | `BigDecimal` | `v7:trailingPE` | 80% | 80% |
| `trailingDividend.*` | C:trailingDividend | `…` | `v7:trailingAnnualDividendRate` → `v7:trailingAnnualDividendYield` | 67% | 67% |

### Crypto — snapshot (+ Session, no TopOfBook) and detail

| field | kind | type | sources (precedence) | coverage | notes |
|---|---|---|---|---|---|
| `marketCap` | R | `BigDecimal` | `v7:marketCap` → `qs:summaryDetail.marketCap` | 100% |  |
| `supply.circulating` | R | `BigDecimal` | `v7:circulatingSupply` → `qs:summaryDetail.circulatingSupply` | 100% |  |
| `supply.total` | R | `BigDecimal` | `v7:totalSupply` → `qs:summaryDetail.totalSupply` | 100% |  |
| `supply.max` | R | `BigDecimal` | `v7:maxSupply` → `qs:summaryDetail.maxSupply` | 100% | 0 for uncapped coins — raw |
| `volume24Hr` | R | `BigDecimal` | `v7:volume24Hr` → `qs:summaryDetail.volume24Hr` | 100% |  |
| `volumeAllCurrencies` | R | `BigDecimal` | `v7:volumeAllCurrencies` → `qs:summaryDetail.volumeAllCurrencies` | 100% |  |
| `fromCurrency` | R | `String` | `v7:fromCurrency` | 100% | coin code, not ISO |
| `toCurrency` | R | `Currency` | `v7:toCurrency` | 100% | ISO |
| `startDate` | R | `LocalDate` | `v7:startDate` → `qs:summaryDetail.startDate` | 100% | epoch s |
| `lastMarket` | R | `String` | `v7:lastMarket` | 100% |  |
| `branding.{image,logo,coinMarketCap}` | R | `URI×3` | `v7:coinImageUrl` → `v7:logoUrl` → `v7:coinMarketCapLink` | 100% |  |
| `detail.name` | R | `String` | `qs:assetProfile.name` | 100% |  |
| `detail.description` | R | `String` | `qs:assetProfile.description` | 100% |  |
| `detail.website` | R | `URI` | `qs:assetProfile.website` | 100% |  |
| `detail.whitepaper` | O | `URI` | `qs:assetProfile.whitepaper` | 92% | 91% |
| `detail.twitter` | O | `String` | `qs:assetProfile.twitter` | 83% | 83% |
| `detail.proofOfWork.{blockNumber,blockReward,netHashesPerSecond}` | C:proofOfWork | `…` | `qs:assetProfile.blockNumber` → `qs:assetProfile.blockReward` → `qs:assetProfile.netHashesPerSecond` | 33% | 33% |
| `detail.fullyDilutedValue` | R | `BigDecimal` | `qs:summaryDetail.fullyDilutedValue` | 100% |  |

### Future — snapshot (+ Session, TopOfBook with Optional sizes; no longName)

| field | kind | type | sources (precedence) | coverage | notes |
|---|---|---|---|---|---|
| `contract.contractSymbol` | R | `boolean` | `v7:contractSymbol` | 100% | Yahoo boolean: is a specific contract |
| `contract.expireDate` | R | `LocalDate` | `v7:expireDate` | 100% | epoch s |
| `contract.openInterest` | R | `long` | `v7:openInterest` | 100% |  |
| `contract.underlyingSymbol` | R | `Symbol` | `v7:underlyingSymbol` | 100% |  |
| `contract.underlyingExchangeSymbol` | R | `String` | `v7:underlyingExchangeSymbol` | 100% |  |
| `contract.headSymbol` | R | `Symbol` | `v7:headSymbolAsString` | 100% |  |

### Index and FxPair

Universal core + Session + TopOfBook. No class-specific fields, no detail tier (indices carry only `defaultKeyStatistics.52WeekChange`, which is already `fiftyTwoWeekChangePercent` in the core).

### Unclassified

Universal core (all R) + `reportedQuoteType: String` + `attempted: Optional<AssetClass>` + `missing: List<String>` + `snapshot: Optional<Instrument>` (set when a detail request, not the snapshot, failed).
