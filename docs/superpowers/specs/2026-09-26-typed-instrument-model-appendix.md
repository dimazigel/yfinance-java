# Appendix A — Field tables (Intrinsic rule)

Kinds: **R** required → non-null component (missing ⇒ downgrade / `Skipped(MODULE_ABSENT)`); **O** optional → `Optional<T>`; **C:name** member of optional cluster `name` (present only if every member is; `(R)` marks a required cluster); **L** list → empty when Yahoo omits.

Coverage = % of live instruments in the wide survey with the value in at least one listed source. Sources: `v7:` = `/v7/finance/quote` field; `qs:` = `/v10/finance/quoteSummary` `module.key`.

Unit rules: the `unit` column is the field's base unit, one of the code's `Unit` names (`PERCENT`, `EPOCH_SECONDS`, `EPOCH_MILLIS`, `EPOCH_DATE`, `ISO_DATE`; blank = `RAW`, the wire value is stored as-is); a `\|PERCENT` suffix on a source overrides the base unit for **that source only**, mirroring `FieldSpec.unit()` and `WirePath.unit()`. The effective unit of a source is its suffix if it has one, else the base unit, and `AppendixConformanceTest` checks that effective unit per source against the code. Every percent field is stored as a fraction; epoch seconds/millis → `Instant`; date-only epochs and ISO date strings → `LocalDate`. Where a field has both a v7 and a quoteSummary source, **v7 serves a percent and quoteSummary already serves a fraction** — for v7 `regularMarketChangePercent`, `postMarketChangePercent`, `dividendYield`, `netExpenseRatio`, `ytdReturn`, `trailingThreeMonthReturns` and `trailingThreeMonthNavReturns` — so only the v7 source carries `\|PERCENT`; `fiveYearAvgDividendYield` and `debtToEquity` are percents in their only source and carry `PERCENT` as the base unit. Verified on the captured fixtures: `price.regularMarketChangePercent` is 0.015331 for AAPL against v7 1.5331 and 0.0054354696 for SPY against v7 0.543547; `price.postMarketChangePercent` is 0.0011443085 (AAPL) and 0.00089453877 (SPY) against v7 0.11443085 and 0.089453876 (the final review found the appendix saying "PERCENT in both sources" for these two rows, which divided the quoteSummary fallback by 100 twice); the fund-snapshot v7 percent fields on SPY/GLD/VFIAX/CSPX.L: v7 expense ratio 0.0945/0.4/0.04/0.07 vs the matching quoteSummary fraction 0.000945/0.004/0.00040/0.00070; v7 `dividendYield` 0.98 vs `summaryDetail.yield` 0.0098; v7 `ytdReturn` 13.07293 vs `fundPerformance.trailingReturns.ytd` 0.1307293; v7 `trailingThreeMonthReturns` 1.65706 vs 0.0165706 — all ten-to-the-second-power percent-vs-fraction pairs. All other yields/margins/held-percent are already fractions in every source. `OptionContract.changePercent` (wire `percentChange`) is likewise a percent on the wire, stored as a fraction. Any field whose unit is marked *verify at impl* must be confirmed against a fixture before the mapper is written. Shorthand rows (`name.*`, `name.{a,b}`) are not unit-checked, since their members may differ (`trailingReturns.asOf` is `EPOCH_DATE`, its siblings `RAW`).

### Universal core (all classes) — coverage shown is the minimum across classes

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `symbol` | R | `Symbol` | `v7:symbol` → `qs:quoteType.symbol` | | 100% |  |
| `shortName` | R | `String` | `v7:shortName` → `qs:quoteType.shortName` → `qs:price.shortName` | | 100% |  |
| `longName` | O | `String` | `v7:longName` → `qs:quoteType.longName` → `qs:price.longName` | | 0% | futures never; 1 equity lacked |
| `currency` | R | `Currency` | `v7:currency` → `qs:price.currency` → `qs:summaryDetail.currency` | | 100% | non-null; pence codes such as `GBp` are not ISO — see design §10 (planned `QuoteCurrency` value type) |
| `exchange` | R | `String` | `v7:exchange` → `qs:quoteType.exchange` → `qs:price.exchange` | | 100% | code, e.g. NMS |
| `fullExchangeName` | R | `String` | `v7:fullExchangeName` → `qs:price.exchangeName` | | 100% |  |
| `exchangeTimezone` | R | `ZoneId` | `v7:exchangeTimezoneName` → `qs:quoteType.timeZoneFullName` | | 100% |  |
| `marketState` | R | `MarketState` | `v7:marketState` → `qs:price.marketState` | | 100% | enum; unknown→OTHER |
| `price` | R | `BigDecimal` | `v7:regularMarketPrice` → `qs:price.regularMarketPrice` | | 100% |  |
| `change` | R | `BigDecimal` | `v7:regularMarketChange` → `qs:price.regularMarketChange` | | 100% |  |
| `changePercent` | R | `BigDecimal` | `v7:regularMarketChangePercent\|PERCENT` → `qs:price.regularMarketChangePercent` | | 100% | v7 PERCENT, `price` module already a fraction (AAPL 1.5331 vs 0.015331) → fraction |
| `previousClose` | R | `BigDecimal` | `v7:regularMarketPreviousClose` → `qs:price.regularMarketPreviousClose` → `qs:summaryDetail.previousClose` | | 100% |  |
| `priceTime` | R | `Instant` | `v7:regularMarketTime` → `qs:price.regularMarketTime` | `EPOCH_SECONDS` | 100% | epoch seconds |
| `fiftyTwoWeekLow` | R | `BigDecimal` | `v7:fiftyTwoWeekLow` → `qs:summaryDetail.fiftyTwoWeekLow` | | 100% |  |
| `fiftyTwoWeekHigh` | R | `BigDecimal` | `v7:fiftyTwoWeekHigh` → `qs:summaryDetail.fiftyTwoWeekHigh` | | 100% |  |
| `fiftyDayAverage` | R | `BigDecimal` | `v7:fiftyDayAverage` → `qs:summaryDetail.fiftyDayAverage` | | 100% |  |
| `twoHundredDayAverage` | R | `BigDecimal` | `v7:twoHundredDayAverage` → `qs:summaryDetail.twoHundredDayAverage` | | 100% |  |
| `averageVolume10Day` | R | `long` | `v7:averageDailyVolume10Day` → `qs:summaryDetail.averageDailyVolume10Day` → `qs:price.averageDailyVolume10Day` | | 100% |  |
| `averageVolume3Month` | R | `long` | `v7:averageDailyVolume3Month` → `qs:summaryDetail.averageVolume` → `qs:price.averageDailyVolume3Month` | | 100% |  |
| `firstTradeDate` | R | `Instant` | `v7:firstTradeDateMilliseconds` | `EPOCH_MILLIS` | 100% | epoch millis |
| `priceHint` | R | `int` | `v7:priceHint` → `qs:price.priceHint` | | 100% |  |
| `hasPrePostMarketData` | R | `boolean` | `v7:hasPrePostMarketData` | | 100% |  |

Coverage drift observed after implementation: live RIDE now returns HTTP 200 with `quoteType NONE` (previously it 404'd like a genuinely unknown symbol). `InstrumentService` treats this as core-incomplete and reports `SkipReason.UNKNOWN_SYMBOL`, the same outcome as a symbol Yahoo's quote endpoint has never heard of — this is a footnote on observed behaviour, not a kind change for any core row.

### Session (all classes except MutualFund)

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `open` | R | `BigDecimal` | `v7:regularMarketOpen` → `qs:price.regularMarketOpen` → `qs:summaryDetail.open` | | 100% |  |
| `dayLow` | R | `BigDecimal` | `v7:regularMarketDayLow` → `qs:price.regularMarketDayLow` → `qs:summaryDetail.dayLow` | | 100% |  |
| `dayHigh` | R | `BigDecimal` | `v7:regularMarketDayHigh` → `qs:price.regularMarketDayHigh` → `qs:summaryDetail.dayHigh` | | 100% |  |
| `volume` | R | `long` | `v7:regularMarketVolume` → `qs:price.regularMarketVolume` → `qs:summaryDetail.volume` | | 100% |  |

### TopOfBook cluster (Equity, Etf, Index, FxPair, Future)

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `bid` | C:book | `BigDecimal` | `v7:bid` → `qs:summaryDetail.bid` | | 99% |  |
| `ask` | C:book | `BigDecimal` | `v7:ask` → `qs:summaryDetail.ask` | | 99% |  |
| `bidSize` | O | `long` | `v7:bidSize` → `qs:summaryDetail.bidSize` | | 98% | inside book, Optional |
| `askSize` | O | `long` | `v7:askSize` → `qs:summaryDetail.askSize` | | 98% | inside book, Optional |

### Equity — snapshot

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `marketCap` | R | `BigDecimal` | `v7:marketCap` → `qs:price.marketCap` → `qs:summaryDetail.marketCap` | | 99% | only miss: BAC-PL (preferred) → downgrade |
| `sharesOutstanding` | R | `long` | `v7:sharesOutstanding` → `qs:defaultKeyStatistics.sharesOutstanding` | | 100% |  |
| `impliedSharesOutstanding` | R | `long` | `v7:impliedSharesOutstanding` → `qs:defaultKeyStatistics.impliedSharesOutstanding` | | 99% | only miss: BAC-PL |
| `financialCurrency` | R | `Currency` | `v7:financialCurrency` → `qs:financialData.financialCurrency` | | 100% |  |
| `nextEarnings.expected` | C:nextEarnings(R) | `Instant` | `v7:earningsTimestamp` → `v7:earningsTimestampStart` → `qs:calendarEvents.earnings.earningsDate.0` | `EPOCH_SECONDS` | 100% | cluster is REQUIRED as a whole |
| `nextEarnings.windowStart` | C:nextEarnings(R) | `Instant` | `v7:earningsTimestampStart` | `EPOCH_SECONDS` | 100% |  |
| `nextEarnings.windowEnd` | C:nextEarnings(R) | `Instant` | `v7:earningsTimestampEnd` | `EPOCH_SECONDS` | 100% |  |
| `nextEarnings.isEstimate` | C:nextEarnings(R) | `boolean` | `v7:isEarningsDateEstimate` | | 100% |  |
| `bookValue` | O | `BigDecimal` | `v7:bookValue` → `qs:defaultKeyStatistics.bookValue` | | 99% | miss: 005930.KS (Samsung) → Optional (Intrinsic) |
| `priceToBook` | O | `BigDecimal` | `v7:priceToBook` → `qs:defaultKeyStatistics.priceToBook` | | 99% | Samsung |
| `trailingEps` | O | `BigDecimal` | `v7:epsTrailingTwelveMonths` → `qs:defaultKeyStatistics.trailingEps` | | 99% | Samsung |
| `forwardEps` | O | `BigDecimal` | `v7:epsForward` → `qs:defaultKeyStatistics.forwardEps` | | 99% | Samsung, BAC-PL |
| `forwardPE` | O | `BigDecimal` | `v7:forwardPE` → `qs:summaryDetail.forwardPE` → `qs:defaultKeyStatistics.forwardPE` | | 99% |  |
| `trailingPE` | O | `BigDecimal` | `v7:trailingPE` → `qs:summaryDetail.trailingPE` | | 82% | loss-makers |
| `trailingDividend.rate` | C:trailingDividend | `BigDecimal` | `v7:trailingAnnualDividendRate` → `qs:summaryDetail.trailingAnnualDividendRate` | | 99% | Samsung |
| `trailingDividend.yield` | C:trailingDividend | `BigDecimal` | `v7:trailingAnnualDividendYield` → `qs:summaryDetail.trailingAnnualDividendYield` | | 99% | fraction in both |
| `currentDividend.rate` | C:currentDividend | `BigDecimal` | `v7:dividendRate` → `qs:summaryDetail.dividendRate` | | 59% |  |
| `currentDividend.yield` | C:currentDividend | `BigDecimal` | `v7:dividendYield\|PERCENT` → `qs:summaryDetail.dividendYield` | | 59% | v7 PERCENT (verified: SPY 0.98 vs summaryDetail 0.0098, Task 7), summaryDetail already a fraction → fraction |
| `currentYearEps.eps` | C:currentYearEps | `BigDecimal` | `v7:epsCurrentYear` | | 92% |  |
| `currentYearEps.priceToEps` | C:currentYearEps | `BigDecimal` | `v7:priceEpsCurrentYear` | | 92% |  |
| `averageAnalystRating` | O | `String` | `v7:averageAnalystRating` | | 90% | display string, e.g. '2.2 - Buy' |
| `postMarketPrice` | C:postMarket | `BigDecimal` | `v7:postMarketPrice` → `qs:price.postMarketPrice` | | 72% |  |
| `postMarketChange` | C:postMarket | `BigDecimal` | `v7:postMarketChange` → `qs:price.postMarketChange` | | 72% |  |
| `postMarketChangePercent` | C:postMarket | `BigDecimal` | `v7:postMarketChangePercent\|PERCENT` → `qs:price.postMarketChangePercent` | | 72% | v7 PERCENT, `price` module already a fraction (AAPL 0.11443085 vs 0.0011443085) → fraction |
| `postMarketTime` | C:postMarket | `Instant` | `v7:postMarketTime` → `qs:price.postMarketTime` | `EPOCH_SECONDS` | 72% | epoch s |

Coverage drift observed after implementation: live PLUG (a former "loss-maker with no pegRatio" example) now reports `statistics.pegRatio` in the detail table below; the field stays `O`, this only changes which symbols exercise the Optional-absent path.

### Equity — detail

Fetched modules: `DetailSpecs.modules(EQUITY)` includes `summaryProfile` alongside `assetProfile` — it is a genuine fallback source, not a leftover, for the `profile.sector`/`.industry`/`.country`/`.website`/`.longBusinessSummary` rows below, each of which already lists it second in precedence.

List-row identifiers: every `analysts.*`/`ownership.*` list below is mapped row-by-row by `RowMappers`, and a row missing its identifying key is dropped (DEBUG count, never a thrown error): `period` for the recommendation-trend, earnings-history and earnings-trend-derived lists; `firm` for `upgradesDowngrades`; `title` for `secFilings`; `organization` for `institutions`/`funds`; `name` for `insiders`; `filerName` for `insiderTransactions`.

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `profile.sector` | R | `String` | `qs:assetProfile.sector` → `qs:summaryProfile.sector` | | 100% |  |
| `profile.industry` | R | `String` | `qs:assetProfile.industry` → `qs:summaryProfile.industry` | | 100% |  |
| `profile.country` | R | `String` | `qs:assetProfile.country` → `qs:summaryProfile.country` | | 100% |  |
| `profile.city` | R | `String` | `qs:assetProfile.city` | | 100% |  |
| `profile.address1` | R | `String` | `qs:assetProfile.address1` | | 100% |  |
| `profile.zip` | R | `String` | `qs:assetProfile.zip` | | 99% |  |
| `profile.website` | R | `URI` | `qs:assetProfile.website` → `qs:summaryProfile.website` | | 100% | lenient URI |
| `profile.longBusinessSummary` | R | `String` | `qs:assetProfile.longBusinessSummary` → `qs:summaryProfile.longBusinessSummary` | | 100% |  |
| `profile.officers` | L | `List<Officer>` | `qs:assetProfile.companyOfficers` | | 100% | may be empty list; ruled Task 18 — a list is never a missing-required entry, this row's earlier `R` was a transcription slip |
| `profile.fullTimeEmployees` | O | `int` | `qs:assetProfile.fullTimeEmployees` | | 98% | COST, AZO, 1299.HK |
| `profile.phone` | O | `String` | `qs:assetProfile.phone` | | 99% |  |
| `profile.state` | O | `String` | `qs:assetProfile.state` | | 66% |  |
| `profile.irWebsite` | O | `URI` | `qs:assetProfile.irWebsite` | | 37% |  |
| `profile.governance.*` | C:governance | `int×5` | `qs:assetProfile.auditRisk` → `qs:assetProfile.boardRisk` → `qs:assetProfile.compensationRisk` → `qs:assetProfile.shareHolderRightsRisk` → `qs:assetProfile.overallRisk` | | 77% | perfect cluster 77.5% |
| `statistics.floatShares` | R | `long` | `qs:defaultKeyStatistics.floatShares` | | 100% |  |
| `statistics.heldPercentInsiders` | R | `BigDecimal` | `qs:defaultKeyStatistics.heldPercentInsiders` | | 100% | fraction |
| `statistics.heldPercentInstitutions` | R | `BigDecimal` | `qs:defaultKeyStatistics.heldPercentInstitutions` | | 100% | fraction |
| `statistics.profitMargins` | R | `BigDecimal` | `qs:defaultKeyStatistics.profitMargins` → `qs:financialData.profitMargins` | | 100% | fraction |
| `statistics.beta` | O | `BigDecimal` | `qs:summaryDetail.beta` → `qs:defaultKeyStatistics.beta` | | 99% | miss: COST, WKHS |
| `statistics.enterpriseValue` | O | `BigDecimal` | `qs:defaultKeyStatistics.enterpriseValue` | | 99% | BNP.PA |
| `statistics.enterpriseToRevenue` | O | `BigDecimal` | `qs:defaultKeyStatistics.enterpriseToRevenue` | | 99% |  |
| `statistics.enterpriseToEbitda` | O | `BigDecimal` | `qs:defaultKeyStatistics.enterpriseToEbitda` | | 90% | 90% |
| `statistics.fiscal.lastFiscalYearEnd` | C:fiscal | `LocalDate` | `qs:defaultKeyStatistics.lastFiscalYearEnd` | `EPOCH_DATE` | 99% | BNP.PA; epoch s → LocalDate |
| `statistics.fiscal.nextFiscalYearEnd` | C:fiscal | `LocalDate` | `qs:defaultKeyStatistics.nextFiscalYearEnd` | `EPOCH_DATE` | 99% |  |
| `statistics.fiscal.mostRecentQuarter` | C:fiscal | `LocalDate` | `qs:defaultKeyStatistics.mostRecentQuarter` | `EPOCH_DATE` | 99% |  |
| `statistics.pegRatio` | O | `BigDecimal` | `qs:defaultKeyStatistics.pegRatio` | | 84% | 84%; PLUG now reports it (previously a documented miss) |
| `statistics.payoutRatio` | O | `BigDecimal` | `qs:summaryDetail.payoutRatio` | | 99% | fraction |
| `statistics.priceToSales` | O | `BigDecimal` | `qs:summaryDetail.priceToSalesTrailing12Months` | | 99% |  |
| `statistics.earningsQuarterlyGrowth` | O | `BigDecimal` | `qs:defaultKeyStatistics.earningsQuarterlyGrowth` | | 79% | 79% |
| `statistics.shortInterest.sharesShort` | C:shortInterest | `long` | `qs:defaultKeyStatistics.sharesShort` | | 75% | 74.6% cluster |
| `statistics.shortInterest.shortRatio` | C:shortInterest | `BigDecimal` | `qs:defaultKeyStatistics.shortRatio` | | 75% |  |
| `statistics.shortInterest.date` | C:shortInterest | `LocalDate` | `qs:defaultKeyStatistics.dateShortInterest` | `EPOCH_DATE` | 75% | epoch s → LocalDate |
| `statistics.shortInterest.sharesShortPriorMonth` | C:shortInterest | `long` | `qs:defaultKeyStatistics.sharesShortPriorMonth` | | 75% |  |
| `statistics.shortInterest.percentSharesOut` | C:shortInterest | `BigDecimal` | `qs:defaultKeyStatistics.sharesPercentSharesOut` | | 75% |  |
| `statistics.shortInterest.percentOfFloat` | O | `BigDecimal` | `qs:defaultKeyStatistics.shortPercentOfFloat` | | 70.5% | **not** a member of the `shortInterest` cluster — it fills independently of the other five |
| `statistics.lastSplit.date` | C:lastSplit | `LocalDate` | `qs:defaultKeyStatistics.lastSplitDate` | `EPOCH_DATE` | 64% | perfect cluster 63.6% |
| `statistics.lastSplit.factor` | C:lastSplit | `String` | `qs:defaultKeyStatistics.lastSplitFactor` | | 64% |  |
| `statistics.lastDividend.value` | C:lastDividend | `BigDecimal` | `qs:defaultKeyStatistics.lastDividendValue` | | 62% | perfect cluster 62.4% |
| `statistics.lastDividend.date` | C:lastDividend | `LocalDate` | `qs:defaultKeyStatistics.lastDividendDate` | `EPOCH_DATE` | 62% |  |
| `statistics.exDividendDate` | O | `LocalDate` | `qs:summaryDetail.exDividendDate` → `qs:calendarEvents.exDividendDate` | `EPOCH_DATE` | 62% | 61.8% |
| `statistics.fiveYearAvgDividendYield` | O | `BigDecimal` | `qs:summaryDetail.fiveYearAvgDividendYield` | `PERCENT` | 57% | 57%; PERCENT → fraction |
| `financials.currentPrice` | R | `BigDecimal` | `qs:financialData.currentPrice` | | 100% |  |
| `financials.totalRevenue` | R | `BigDecimal` | `qs:financialData.totalRevenue` | | 100% |  |
| `financials.revenuePerShare` | R | `BigDecimal` | `qs:financialData.revenuePerShare` | | 100% |  |
| `financials.grossProfits` | R | `BigDecimal` | `qs:financialData.grossProfits` | | 100% |  |
| `financials.margins.gross` | R | `BigDecimal` | `qs:financialData.grossMargins` | | 100% | fraction |
| `financials.margins.operating` | R | `BigDecimal` | `qs:financialData.operatingMargins` | | 100% |  |
| `financials.margins.ebitda` | R | `BigDecimal` | `qs:financialData.ebitdaMargins` | | 100% |  |
| `financials.totalCash` | R | `BigDecimal` | `qs:financialData.totalCash` | | 100% |  |
| `financials.totalCashPerShare` | R | `BigDecimal` | `qs:financialData.totalCashPerShare` | | 100% |  |
| `financials.totalDebt` | R | `BigDecimal` | `qs:financialData.totalDebt` | | 100% |  |
| `financials.revenueGrowth` | O | `BigDecimal` | `qs:financialData.revenueGrowth` | | 99% | NNE, VOD.L |
| `financials.debtToEquity` | O | `BigDecimal` | `qs:financialData.debtToEquity` | `PERCENT` | 86% | 86%; PERCENT |
| `financials.ebitda` | O | `BigDecimal` | `qs:financialData.ebitda` | | 90% | 90% |
| `financials.freeCashflow` | O | `BigDecimal` | `qs:financialData.freeCashflow` | | 88% | 88% |
| `financials.operatingCashflow` | O | `BigDecimal` | `qs:financialData.operatingCashflow` | | 97% | 97% |
| `financials.returnOnEquity` | O | `BigDecimal` | `qs:financialData.returnOnEquity` | | 94% | 94% |
| `financials.returnOnAssets` | O | `BigDecimal` | `qs:financialData.returnOnAssets` | | 99% | 99% |
| `financials.liquidity.*` | C:liquidity | `BigDecimal×2` | `qs:financialData.currentRatio` → `qs:financialData.quickRatio` | | 91% | perfect 90.8% |
| `financials.earningsGrowth` | O | `BigDecimal` | `qs:financialData.earningsGrowth` | | 77% | 76% |
| `analysts.recommendationKey` | R | `String` | `qs:financialData.recommendationKey` | | 100% | buy/hold/… vocabulary |
| `analysts.targets.*` | C:targets | `BigDecimal×4 + int` | `qs:financialData.targetLowPrice` → `qs:financialData.targetMeanPrice` → `qs:financialData.targetMedianPrice` → `qs:financialData.targetHighPrice` → `qs:financialData.numberOfAnalystOpinions` | | 97% | perfect 96.5% |
| `analysts.rating.mean` | C:rating | `BigDecimal` | `qs:financialData.recommendationMean` | | 90% | sole member of cluster `rating` (Task 12) |
| `analysts.rating.averageAnalystRating` | O | `String` | `v7:averageAnalystRating` | | 90% | plain optional, **not** part of the `rating` cluster — the v7 row is often absent when detail is fetched from quoteSummary alone and must not empty the whole rating (Task 12) |
| `analysts.recommendationTrend` | L | `List<RecommendationPeriod>` | `qs:recommendationTrend.trend` | | 98% | list |
| `analysts.earningsHistory` | L | `List<…>` | `qs:earningsHistory.history` | | 92% |  |
| `analysts.earningsEstimates` | L | `List<PeriodEstimate>` | `qs:earningsTrend.trend` | | 100% | one of five lists derived from the same `earningsTrend.trend` module (see below) |
| `analysts.revenueEstimates` | L | `List<PeriodEstimate>` | `qs:earningsTrend.trend` | | 100% |  |
| `analysts.epsTrend` | L | `List<EpsTrendPeriod>` | `qs:earningsTrend.trend` | | 100% |  |
| `analysts.epsRevisions` | L | `List<EpsRevisionsPeriod>` | `qs:earningsTrend.trend` | | 100% |  |
| `analysts.growthEstimates` | L | `List<GrowthEstimate>` | `qs:earningsTrend.trend` | | 100% |  |
| `analysts.upgradesDowngrades` | L | `List<…>` | `qs:upgradeDowngradeHistory.history` | | 74% | module 73% → empty list |
| `analysts.secFilings` | L | `List<SecFiling>` | `qs:secFilings.filings` | | 69% | module 69% → empty |
| `ownership.breakdown.*` | R | `BigDecimal×3 + int` | `qs:majorHoldersBreakdown.insidersPercentHeld` → `qs:majorHoldersBreakdown.institutionsPercentHeld` → `qs:majorHoldersBreakdown.institutionsFloatPercentHeld` → `qs:majorHoldersBreakdown.institutionsCount` | | 100% | 98% module |
| `ownership.institutions` | L | `List<InstitutionalHolder>` | `qs:institutionOwnership.ownershipList` | | 96% |  |
| `ownership.funds` | L | `List<InstitutionalHolder>` | `qs:fundOwnership.ownershipList` | | 96% |  |
| `ownership.insiders` | L | `List<InsiderHolder>` | `qs:insiderHolders.holders` | | 79% |  |
| `ownership.insiderTransactions` | L | `List<InsiderTransaction>` | `qs:insiderTransactions.transactions` | | 79% | module 80% → empty |
| `ownership.netSharePurchaseActivity` | R | `NetSharePurchaseActivity` | `qs:netSharePurchaseActivity.period` | | 100% | 98% module |

`analysts.estimates` (a single row in earlier drafts of this appendix) is the five rows above (`earningsEstimates`, `revenueEstimates`, `epsTrend`, `epsRevisions`, `growthEstimates`): each is its own model list, but all five are sourced from the one `earningsTrend.trend` module and populated by one pass over its rows.

`statistics.shortInterest.*` (a single clustered row in earlier drafts) is the five `C:shortInterest` rows above; `percentOfFloat` shares the `statistics.shortInterest.` name prefix but is deliberately excluded from the cluster, as already noted in earlier drafts ("shortPercentOfFloat Optional inside").

### Etf — snapshot (+ Session, TopOfBook)

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `netAssets` | O | `BigDecimal` | `v7:netAssets` → `qs:defaultKeyStatistics.totalAssets` → `qs:summaryDetail.totalAssets` | | 87% | 87%: UCITS lack |
| `expenseRatio` | O | `BigDecimal` | `v7:netExpenseRatio\|PERCENT` → `qs:fundProfile.feesExpensesInvestment.annualReportExpenseRatio` | | 89% | 89%: some listings lack it (was "UCITS lack it everywhere"; CSPX.L, previously a documented miss, now reports an expense ratio in both sources); unit verified in Task 7 (see header) |
| `yield` | O | `BigDecimal` | `v7:dividendYield\|PERCENT` → `qs:summaryDetail.yield` → `qs:defaultKeyStatistics.yield` | | 82% | 82%; v7 PERCENT verified against summaryDetail fraction (Task 7, see header) |
| `navPrice` | O | `BigDecimal` | `qs:summaryDetail.navPrice` | | 92% | 92% |
| `beta3Year` | O | `BigDecimal` | `qs:defaultKeyStatistics.beta3Year` | | 82% | 82% |
| `ytdReturn` | R | `BigDecimal` | `v7:ytdReturn\|PERCENT` → `qs:defaultKeyStatistics.ytdReturn` → `qs:fundPerformance.trailingReturns.ytd` | | 100% | 100% via fundPerformance; v7 PERCENT (see header) |
| `threeMonthReturn` | R | `BigDecimal` | `v7:trailingThreeMonthReturns\|PERCENT` → `qs:fundPerformance.trailingReturns.threeMonth` | | 100% | 100%; v7 PERCENT (see header) |
| `trailingThreeMonthNavReturns` | O | `BigDecimal` | `v7:trailingThreeMonthNavReturns\|PERCENT` | | 82% | 82%; v7 PERCENT (see header) |
| `equityLikeStats.*` | C:equityLikeStats | `BigDecimal×2 + long + Currency` | `v7:bookValue` → `v7:priceToBook` → `v7:sharesOutstanding` → `v7:financialCurrency` | | 58% | perfect 58%; GLD, previously a documented miss for the whole cluster, now reports it in full |
| `trailingPE` | O | `BigDecimal` | `v7:trailingPE` → `qs:summaryDetail.trailingPE` | | 66% | 66% |
| `trailingDividend.*` | C:trailingDividend | `BigDecimal×2` | `v7:trailingAnnualDividendRate` → `v7:trailingAnnualDividendYield` | | 71% | 71% |
| `postMarketPrice` | C:postMarket | `BigDecimal` | `v7:postMarketPrice` → `qs:price.postMarketPrice` | | 68% |  |
| `postMarketChange` | C:postMarket | `BigDecimal` | `v7:postMarketChange` → `qs:price.postMarketChange` | | 68% |  |
| `postMarketChangePercent` | C:postMarket | `BigDecimal` | `v7:postMarketChangePercent\|PERCENT` → `qs:price.postMarketChangePercent` | | 68% | v7 PERCENT, `price` module already a fraction (AAPL 0.11443085 vs 0.0011443085) → fraction |
| `postMarketTime` | C:postMarket | `Instant` | `v7:postMarketTime` → `qs:price.postMarketTime` | `EPOCH_SECONDS` | 68% | epoch s |

`legalType` moved to "Etf — detail" below: it is populated from the detail tier (`EtfDetailSpecs`), not the snapshot.

### Etf — detail

Own fields of `EtfDetailSpecs`, in addition to "Fund detail shared by Etf and MutualFund" below (`EtfDetailSpecs.DETAIL` is the concatenation of both).

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `legalType` | R | `String` | `qs:fundProfile.legalType` → `qs:defaultKeyStatistics.legalType` | | 100% | moved here from the snapshot table (it is a detail-tier field) |
| `longBusinessSummary` | O | `String` | `qs:assetProfile.longBusinessSummary` | | 71% | per class: ETF is `O` (MutualFund's own copy is `R` — see "MutualFund — detail"); no longer a shared row |
| `beta3Year` | O | `BigDecimal` | `qs:defaultKeyStatistics.beta3Year` | | — | ruled Task 18 — added; coverage not measured in the survey; optional by construction. Distinct from the *snapshot*-tier `beta3Year` in "Etf — snapshot" above, a separate field |
| `styleBoxUrl` | O | `URI` | `qs:fundProfile.styleBoxUrl` | | — | ruled Task 18 — added; coverage not measured in the survey; optional by construction |

### Fund detail shared by Etf and MutualFund (coverage shown for ETF)

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `family` | R | `String` | `qs:fundProfile.family` → `qs:defaultKeyStatistics.fundFamily` | | 100% |  |
| `inceptionDate` | R | `LocalDate` | `qs:defaultKeyStatistics.fundInceptionDate` | `EPOCH_DATE` | 100% | epoch s |
| `trailingReturns.{ytd,oneMonth,threeMonth,oneYear,threeYear,fiveYear,tenYear,asOf}` | R | `BigDecimal×7 + LocalDate` | `qs:fundPerformance.trailingReturns.ytd` | | 100% | fractions; `asOf` is `EPOCH_DATE` (`qs:fundPerformance.trailingReturns.asOfDate`); 100% both classes |
| `annualTotalReturns` | L | `List<YearReturn>` | `qs:fundPerformance.annualTotalReturns.returns` | | 100% | ruled Task 18 — a list is never a missing-required entry, this row's earlier `R` was a transcription slip |
| `allocation.{stock,bond,cash,preferred,convertible,other}` | R | `BigDecimal×6` | `qs:topHoldings.stockPosition` | | 100% | fractions; 100% |
| `equityValuation.{priceToEarnings,priceToBook,priceToSales,priceToCashflow}` | R | `BigDecimal×4` | `qs:topHoldings.equityHoldings.priceToEarnings` | | 100% | 100% |
| `holdings` | L | `List<Holding>` | `qs:topHoldings.holdings` | | 82% | empty for metal/crypto ETFs (82% ETF, 93% MF) |
| `sectorWeightings` | L | `List<SectorWeight>` | `qs:topHoldings.sectorWeightings` | | 74% | 74% ETF |
| `bondRatings` | L | `List<BondRating>` | `qs:topHoldings.bondRatings` | | 97% | 97% ETF |
| `category` | O | `String` | `qs:fundProfile.categoryName` → `qs:defaultKeyStatistics.category` → `qs:fundPerformance.fundCategoryName` | | 71% | 71% ETF / 67% MF |

`longBusinessSummary` moved out of this shared table: it is per class now (ETF `O`, MutualFund `R` — see "Etf — detail" and "MutualFund — detail").

### MutualFund — snapshot (no Session, no TopOfBook)

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `netAssets` | R | `BigDecimal` | `v7:netAssets` → `qs:defaultKeyStatistics.totalAssets` → `qs:summaryDetail.totalAssets` | | 100% | US sample n=15 |
| `expenseRatio` | R | `BigDecimal` | `v7:netExpenseRatio\|PERCENT` → `qs:fundProfile.feesExpensesInvestment.annualReportExpenseRatio` → `qs:defaultKeyStatistics.annualReportExpenseRatio` | | 100% | v7 PERCENT (see header) |
| `yield` | R | `BigDecimal` | `v7:dividendYield\|PERCENT` → `qs:summaryDetail.yield` | | 100% | v7 PERCENT → fraction |
| `dividendRate` | R | `BigDecimal` | `v7:dividendRate` | | 100% |  |
| `ytdReturn` | R | `BigDecimal` | `v7:ytdReturn\|PERCENT` → `qs:summaryDetail.ytdReturn` → `qs:fundPerformance.trailingReturns.ytd` | | 100% | v7 PERCENT (see header) |
| `threeMonthReturn` | R | `BigDecimal` | `v7:trailingThreeMonthReturns\|PERCENT` → `qs:fundPerformance.trailingReturns.threeMonth` | | 100% | v7 PERCENT (see header) |
| `equityLikeStats.*` | C:equityLikeStats | `…` | `v7:bookValue` → `v7:priceToBook` → `v7:sharesOutstanding` → `v7:financialCurrency` | | 60% | 60% |
| `trailingPE` | O | `BigDecimal` | `v7:trailingPE` | | 80% | 80% |
| `trailingDividend.*` | C:trailingDividend | `…` | `v7:trailingAnnualDividendRate` → `v7:trailingAnnualDividendYield` | | 67% | 67% |

### MutualFund — detail

Own fields of `MutualFundDetailSpecs`, in addition to "Fund detail shared by Etf and MutualFund" above (`MutualFundDetailSpecs.DETAIL` is the concatenation of both). These are the rows earlier drafts of this appendix marked "detail" inside a single combined MutualFund table.

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `longBusinessSummary` | R | `String` | `qs:assetProfile.longBusinessSummary` | | 100% | per class: MutualFund is `R` (ETF's own copy is `O` — see "Etf — detail"); no longer a shared row |
| `morningstar.{overall,risk}` | R | `int×2` | `qs:defaultKeyStatistics.morningStarOverallRating` → `qs:defaultKeyStatistics.morningStarRiskRating` | | 100% |  |
| `annualHoldingsTurnover` | R | `BigDecimal` | `qs:defaultKeyStatistics.annualHoldingsTurnover` | | 100% |  |
| `lastCapGain` | R | `BigDecimal` | `qs:defaultKeyStatistics.lastCapGain` | | 100% |  |
| `lastDividendValue` | R | `BigDecimal` | `qs:defaultKeyStatistics.lastDividendValue` | | 100% |  |
| `beta3Year` | R | `BigDecimal` | `qs:defaultKeyStatistics.beta3Year` | | 100% |  |
| `minimums.{initial,subsequent}` | R | `BigDecimal×2` | `qs:fundProfile.initInvestment` → `qs:fundProfile.subseqInvestment` | | 100% |  |
| `brokerages` | L | `List<String>` | `qs:fundProfile.brokerages` | | 100% | ruled Task 18 — a list is never a missing-required entry, this row's earlier `R` was a transcription slip |
| `loadAdjustedReturns.{oneYear,threeYear,fiveYear,tenYear}` | R | `BigDecimal×4` | `qs:fundPerformance.loadAdjustedReturns.oneYear` | | 100% | ruled Task 18 — expanded to match code's 4 separate fields, no cluster (non-null members) |
| `rankInCategory.{ytd,oneMonth,threeMonth,oneYear,threeYear,fiveYear}` | R | `BigDecimal×6` | `qs:fundPerformance.rankInCategory.ytd` | | 100% | ruled Task 18 — expanded to match code's 6 separate fields, no cluster (non-null members) |
| `styleBoxUrl` | R | `URI` | `qs:fundProfile.styleBoxUrl` | | 100% |  |

### Crypto — snapshot (+ Session, no TopOfBook)

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `marketCap` | R | `BigDecimal` | `v7:marketCap` → `qs:summaryDetail.marketCap` | | 100% |  |
| `supply.circulating` | R | `BigDecimal` | `v7:circulatingSupply` → `qs:summaryDetail.circulatingSupply` | | 100% |  |
| `supply.total` | R | `BigDecimal` | `v7:totalSupply` → `qs:summaryDetail.totalSupply` | | 100% |  |
| `supply.max` | R | `BigDecimal` | `v7:maxSupply` → `qs:summaryDetail.maxSupply` | | 100% | 0 for uncapped coins — raw |
| `volume24Hr` | R | `BigDecimal` | `v7:volume24Hr` → `qs:summaryDetail.volume24Hr` | | 100% |  |
| `volumeAllCurrencies` | R | `BigDecimal` | `v7:volumeAllCurrencies` → `qs:summaryDetail.volumeAllCurrencies` | | 100% |  |
| `fromCurrency` | R | `String` | `v7:fromCurrency` → `qs:summaryDetail.fromCurrency` | | 100% | coin code, not ISO |
| `toCurrency` | R | `Currency` | `v7:toCurrency` → `qs:summaryDetail.toCurrency` | | 100% | ISO; arrives as an FX ticker (e.g. `USD=X`) — the builder strips the `=X` suffix so the ISO code resolves |
| `startDate` | R | `LocalDate` | `v7:startDate` → `qs:summaryDetail.startDate` | `EPOCH_DATE` | 100% | epoch s |
| `lastMarket` | R | `String` | `v7:lastMarket` → `qs:summaryDetail.lastMarket` | | 100% |  |
| `branding.{image,logo,coinMarketCap}` | R | `URI×3` | `v7:coinImageUrl` → `v7:logoUrl` → `v7:coinMarketCapLink` → `qs:summaryDetail.coinMarketCapLink` | | 100% | the fourth path is `coinMarketCap`'s own qs fallback only |

### Crypto — detail

`CryptoDetailSpecs.DETAIL` fields (obtained through the detail tier, `CryptoDetail` record): identity from `assetProfile`, valuation from `summaryDetail`. Earlier drafts of this appendix combined this table with "Crypto — snapshot" above and prefixed these rows `detail.*` as a reading aid — code has no such prefix on these field names, so it is dropped here to match exactly.

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `name` | R | `String` | `qs:assetProfile.name` | | 100% |  |
| `description` | R | `String` | `qs:assetProfile.description` | | 100% |  |
| `website` | R | `URI` | `qs:assetProfile.website` | | 100% |  |
| `startDate` | R | `LocalDate` | `qs:assetProfile.startDate` | `ISO_DATE` | 100% | ISO date string (fixture value `"2010-07-13"`); ruled Task 18 — distinct from the *snapshot* `startDate` above (`v7:startDate` → `qs:summaryDetail.startDate`, an epoch value) |
| `whitepaper` | O | `URI` | `qs:assetProfile.whitepaper` | | 92% | 91% |
| `twitter` | O | `String` | `qs:assetProfile.twitter` | | 83% | 83% |
| `proofOfWork.{blockNumber,blockReward,netHashesPerSecond}` | C:proofOfWork | `…` | `qs:assetProfile.blockNumber` → `qs:assetProfile.blockReward` → `qs:assetProfile.netHashesPerSecond` | | 33% | 33% |
| `fullyDilutedValue` | R | `BigDecimal` | `qs:summaryDetail.fullyDilutedValue` | | 100% |  |

### Future — snapshot (+ Session, TopOfBook with Optional sizes; no longName)

| field | kind | type | sources (precedence) | unit | coverage | notes |
|---|---|---|---|---|---|---|
| `contract.contractSymbol` | R | `boolean` | `v7:contractSymbol` | | 100% | Yahoo boolean: is a specific contract; the record component keeps this name in code even though it reads as `isSpecificContract` |
| `contract.expireDate` | R | `LocalDate` | `v7:expireDate` | `EPOCH_DATE` | 100% | epoch s |
| `contract.openInterest` | R | `long` | `v7:openInterest` | | 100% |  |
| `contract.underlyingSymbol` | R | `Symbol` | `v7:underlyingSymbol` | | 100% | the front-month contract, e.g. `ESZ26.CME` |
| `contract.underlyingExchangeSymbol` | R | `String` | `v7:underlyingExchangeSymbol` | | 100% |  |
| `contract.headSymbol` | R | `Symbol` | `v7:headSymbolAsString` | | 100% | the continuous root, e.g. `ES=F` |

### Index and FxPair

Universal core + Session + TopOfBook. No class-specific fields, no detail tier (indices carry only `defaultKeyStatistics.52WeekChange`, which is already `fiftyTwoWeekChangePercent` in the core).

### Unclassified

Universal core (all R) + `reportedQuoteType: String` + `attempted: Optional<AssetClass>` + `missing: List<String>` + `snapshot: Optional<Instrument>` (set when a detail request, not the snapshot, failed).

### OptionContract (`/v7/finance/options`; not part of the `FieldSpec`/assembly system)

`OptionsMapper` maps the options wire response directly to `OptionContract`/`OptionChain` — there is no `FieldSpec` table for it, so it is not covered by the Task 18 conformance test; documented here for completeness only.

`contractSymbol`, `type` (call/put, from which bucket the contract came), `strike` and `expiration` identify the contract and are always present by construction. The remaining columns follow a per-row survey of 987 live contracts across 7 chains (6 underlyings: AAPL at two expirations, SPY, `^SPX`, GLD, PLUG, TSLA): a contract missing any key found in 100% of the survey is dropped entirely (DEBUG count, never thrown).

| field | kind | type | source | coverage | notes |
|---|---|---|---|---|---|
| `contractSymbol` | R | `String` | wire `contractSymbol` | 100% |  |
| `type` | R | `OptionType` | calls/puts bucket | 100% |  |
| `strike` | R | `BigDecimal` | wire `strike` | 100% |  |
| `expiration` | R | `Instant` | wire `expiration` | 100% | epoch s |
| `currency` | R | `QuoteCurrency` | wire `currency` | 100% |  |
| `lastPrice` | R | `BigDecimal` | wire `lastPrice` | 100% |  |
| `change` | R | `BigDecimal` | wire `change` | 100% |  |
| `changePercent` | R | `BigDecimal` | wire `percentChange` | 100% | PERCENT on the wire → fraction (11.67 → 0.1167) |
| `ask` | R | `BigDecimal` | wire `ask` | 100% |  |
| `contractSize` | R | `String` | wire `contractSize` | 100% |  |
| `lastTradeDate` | R | `Instant` | wire `lastTradeDate` | 100% | epoch s |
| `impliedVolatility` | R | `BigDecimal` | wire `impliedVolatility` | 100% |  |
| `inTheMoney` | R | `boolean` | wire `inTheMoney` | 100% |  |
| `bid` | O | `BigDecimal` | wire `bid` | 99.7% | absent for contracts with no resting bid |
| `openInterest` | O | `long` | wire `openInterest` | 97.2% |  |
| `volume` | O | `long` | wire `volume` | 95.4% |  |

`OptionChain` itself is `Optional<OptionChain>`: empty when `expirationDates` is empty (the underlying has no listed options at all — a discovered capability, not a promise tied to any asset class). Once `expirationDates` is non-empty a chain is promised for it; a response with no `options` bucket, or no `expirationDate`, for that promised chain throws `YFDataException` rather than downgrading.

### PriceBar / HistoryMetadata (`/v8/finance/chart`; not part of the `FieldSpec`/assembly system)

`ChartMapper` maps the chart wire response directly to `PriceBar`/`HistoryMetadata`/`PriceHistory` — there is no `FieldSpec` table for it, so it is not covered by the Task 18 conformance test; documented here for completeness only.

`PriceBar` — one OHLCV candle:

| field | kind | type | source | notes |
|---|---|---|---|---|
| `timestamp` | R | `Instant` | chart `timestamp[i]` | epoch s |
| `open` | R | `BigDecimal` | chart `indicators.quote[0].open[i]` |  |
| `high` | R | `BigDecimal` | chart `indicators.quote[0].high[i]` |  |
| `low` | R | `BigDecimal` | chart `indicators.quote[0].low[i]` |  |
| `close` | R | `BigDecimal` | chart `indicators.quote[0].close[i]` |  |
| `adjClose` | O | `BigDecimal` | chart `indicators.adjclose[0].adjclose[i]` | absent if Yahoo doesn't provide it |
| `volume` | O | `long` | chart `indicators.quote[0].volume[i]` | never coerced to 0 when Yahoo reports none |

OHLC (`open`/`high`/`low`/`close`) is non-null by construction: a bar missing any one of the four is dropped entirely (Yahoo pads intraday series with all-null rows for halts and pre-open gaps, and occasionally omits just one of the four); `ChartMapper` logs the dropped count at DEBUG. `adjClose` and `volume` are `Optional`. `PriceBar.adjusted()` / `PriceHistory.adjusted()` scale OHLC by `adjClose / close` for Python yfinance's `auto_adjust=True` view; raw bars are otherwise unadjusted — the library never adjusts silently.

`HistoryMetadata` — instrument metadata alongside a price-history response. Every field is required except `dataGranularity`, a documented deviation from design §4.4: `Optional<Interval>` because Yahoo can report an interval string this version's `Interval` enum does not know, and that must not fail the whole history. Any other field missing makes `ChartMapper` throw `YFDataException` rather than return a partially-populated record — a price history has no downgrade tier, unlike a quote or a detail.

| field | kind | type | source | notes |
|---|---|---|---|---|
| `symbol` | R | `Symbol` | chart `meta.symbol` |  |
| `currency` | R | `QuoteCurrency` | chart `meta.currency` |  |
| `exchangeName` | R | `String` | chart `meta.exchangeName` |  |
| `fullExchangeName` | R | `String` | chart `meta.fullExchangeName` |  |
| `instrumentType` | R | `String` | chart `meta.instrumentType` |  |
| `timezone` | R | `ZoneId` | chart `meta.exchangeTimezoneName` |  |
| `firstTradeDate` | R | `Instant` | chart `meta.firstTradeDate` | epoch s |
| `regularMarketPrice` | R | `BigDecimal` | chart `meta.regularMarketPrice` |  |
| `previousClose` | R | `BigDecimal` | chart `meta.chartPreviousClose` |  |
| `regularMarketTime` | R | `Instant` | chart `meta.regularMarketTime` | epoch s |
| `priceHint` | R | `int` | chart `meta.priceHint` |  |
| `dataGranularity` | O | `Interval` | chart `meta.dataGranularity` | documented deviation from design §4.4 |
| `validRanges` | L | `List<Range>` | chart `meta.validRanges` | unknown values dropped |
| `currentTradingPeriod` | R | `TradingPeriods` | chart `meta.currentTradingPeriod` | pre/regular/post, all three or the field is missing |
| `hasPrePostMarketData` | R | `boolean` | chart `meta.hasPrePostMarketData` |  |

### Data flow amendment: single-symbol snapshot fallback (design §6.3, Task 17)

The single-symbol snapshot fallback (used when a batch quoteSummary call didn't cover a symbol, or for a lone `Ticker` lookup that only needs the snapshot) does not always request just `price,summaryDetail,quoteType`. `SnapshotSpecs.fallbackModules(AssetClass)` starts from those three (`SnapshotSpecs.BASE_FALLBACK_MODULES`) and extends them with every other quoteSummary module that class's own snapshot `FieldSpec`s (`SnapshotSpecs.forClass(AssetClass)`) reference — derived from the specs themselves rather than hand-maintained, so a class whose guarantee moves to a new module picks up the fallback automatically. For example, ETF and MutualFund add `fundPerformance`, `defaultKeyStatistics` and `fundProfile` because their own snapshot fields (`ytdReturn`, `threeMonthReturn`, `equityLikeStats.*`, etc.) reach into those modules. It is still one request per symbol. This was found by the first live drift run: all 7 UCITS ETFs in the survey were downgraded under the old fixed three-module fallback, because their `ytdReturn`/`threeMonthReturn` guarantee only resolved through `fundPerformance`, which the fixed set never requested.
