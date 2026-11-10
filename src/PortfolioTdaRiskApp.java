import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
public class PortfolioTdaRiskApp {
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        SwingUtilities.invokeLater(() -> {
            AnalysisController controller = new AnalysisController();
            controller.run();
            MainFrame frame = new MainFrame(controller);
            frame.setVisible(true);
        });
    }
    static final class Config {
        final int assetCount;
        final int tradingDays;
        final int randomPortfolios;
        final double confidenceLevel;
        final double initialPrice;
        final long seed;
        Config() {
            this.assetCount = 12;
            this.tradingDays = 756;
            this.randomPortfolios = 7000;
            this.confidenceLevel = 0.95;
            this.initialPrice = 100.0;
            this.seed = 42L;
        }
    }
    static final class AnalysisController {
        final Config config;
        MarketData marketData;
        FeatureData featureData;
        PersistenceResult persistenceResult;
        PortfolioResult equalWeightResult;
        PortfolioResult minCvarResult;
        PortfolioResult diversifiedResult;
        List<PortfolioResult> sampledPortfolios;
        RiskReport riskReport;
        AnalysisController() {
            this.config = new Config();
            this.sampledPortfolios = new ArrayList<>();
        }
        void run() {
            MarketSimulator simulator = new MarketSimulator(config);
            this.marketData = simulator.simulate();
            FeatureExtractor extractor = new FeatureExtractor();
            this.featureData = extractor.extract(marketData);
            TopologicalAnalyzer analyzer = new TopologicalAnalyzer();
            this.persistenceResult = analyzer.analyze(featureData.normalizedFeatures);
            PortfolioEngine engine = new PortfolioEngine(config);
            this.equalWeightResult = engine.equalWeightPortfolio(marketData);
            this.sampledPortfolios = engine.sampleRandomPortfolios(marketData, featureData, config.randomPortfolios);
            this.minCvarResult = engine.bestByCvar(sampledPortfolios);
            this.diversifiedResult = engine.bestByTopologyAwareScore(sampledPortfolios);
            RiskReportBuilder builder = new RiskReportBuilder();
            this.riskReport = builder.build(marketData, featureData, persistenceResult, equalWeightResult, minCvarResult, diversifiedResult, sampledPortfolios);
        }
    }
    static final class MainFrame extends JFrame {
        final AnalysisController controller;
        MainFrame(AnalysisController controller) {
            super("TDA Portfolio Risk Modeling in Java");
            this.controller = controller;
            setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            setPreferredSize(new Dimension(1280, 860));
            setLayout(new BorderLayout());
            add(buildHeader(), BorderLayout.NORTH);
            add(buildTabs(), BorderLayout.CENTER);
            pack();
            setLocationRelativeTo(null);
        }
        JPanel buildHeader() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            JLabel title = new JLabel("Τοπολογική Ανάλυση Δεδομένων για Μοντελοποίηση και Μείωση Κινδύνου Χαρτοφυλακίων");
            title.setFont(new Font("Serif", Font.BOLD, 20));
            JLabel subtitle = new JLabel("Πλήρης Java εφαρμογή με προσομοίωση αγοράς, persistence, VaR/CVaR, βελτιστοποίηση και 6 γραφήματα Swing");
            subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
            panel.add(title, BorderLayout.NORTH);
            panel.add(subtitle, BorderLayout.SOUTH);
            return panel;
        }
        JTabbedPane buildTabs() {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Σύνοψη", new JScrollPane(buildSummaryArea()));
            tabs.addTab("1. Τιμές", new PriceChartPanel(controller.marketData));
            tabs.addTab("2. Συσχέτιση", new CorrelationHeatmapPanel(controller.marketData));
            tabs.addTab("3. Risk-Return", new RiskReturnScatterPanel(controller.sampledPortfolios, controller.equalWeightResult, controller.minCvarResult, controller.diversifiedResult));
            tabs.addTab("4. Persistence Diagram", new PersistenceDiagramPanel(controller.persistenceResult));
            tabs.addTab("5. Barcode", new BarcodePanel(controller.persistenceResult));
            tabs.addTab("6. Μείωση Κινδύνου", new PortfolioComparisonPanel(controller.marketData, controller.equalWeightResult, controller.minCvarResult, controller.diversifiedResult));
            return tabs;
        }
        JTextArea buildSummaryArea() {
            JTextArea area = new JTextArea();
            area.setFont(new Font("Monospaced", Font.PLAIN, 13));
            area.setEditable(false);
            area.setText(controller.riskReport.text);
            area.setCaretPosition(0);
            return area;
        }
    }
    static final class MarketData {
        final String[] assetNames;
        final double[][] returns;
        final double[][] prices;
        final double[][] correlation;
        final double[][] covariance;
        final double[] marketReturns;
        MarketData(String[] assetNames, double[][] returns, double[][] prices, double[] marketReturns) {
            this.assetNames = assetNames;
            this.returns = returns;
            this.prices = prices;
            this.marketReturns = marketReturns;
            this.covariance = MatrixOps.covariance(returns);
            this.correlation = MatrixOps.correlation(returns);
        }
        int days() {
            return returns.length;
        }
        int assets() {
            return assetNames.length;
        }
        double[] assetReturns(int asset) {
            double[] r = new double[returns.length];
            for (int t = 0; t < returns.length; t++) {
                r[t] = returns[t][asset];
            }
            return r;
        }
        double[] assetPrices(int asset) {
            double[] p = new double[prices.length];
            for (int t = 0; t < prices.length; t++) {
                p[t] = prices[t][asset];
            }
            return p;
        }
    }
    static final class MarketSimulator {
        final Config config;
        final Random random;
        MarketSimulator(Config config) {
            this.config = config;
            this.random = new Random(config.seed);
        }
        MarketData simulate() {
            String[] names = defaultAssetNames(config.assetCount);
            double[][] returns = new double[config.tradingDays][config.assetCount];
            double[][] prices = new double[config.tradingDays + 1][config.assetCount];
            double[] market = new double[config.tradingDays];
            for (int i = 0; i < config.assetCount; i++) {
                prices[0][i] = config.initialPrice + 3.5 * i;
            }
            double[] beta = assetBetas(config.assetCount);
            double[] alpha = assetAlphas(config.assetCount);
            double[] idioVol = assetIdioVols(config.assetCount);
            for (int t = 0; t < config.tradingDays; t++) {
                MarketRegime regime = regimeForDay(t, config.tradingDays);
                double systematicShock = random.nextGaussian();
                double marketReturn = regime.dailyDrift + regime.dailyVolatility * systematicShock;
                if (random.nextDouble() < regime.jumpProbability) {
                    marketReturn += regime.jumpMean + regime.jumpVolatility * random.nextGaussian();
                }
                market[t] = marketReturn;
                for (int a = 0; a < config.assetCount; a++) {
                    double sectorShock = sectorShock(a, t);
                    double idioShock = idioVol[a] * random.nextGaussian();
                    double raw = alpha[a] + beta[a] * marketReturn + sectorShock + idioShock;
                    double bounded = Math.max(-0.18, Math.min(0.18, raw));
                    returns[t][a] = bounded;
                    prices[t + 1][a] = prices[t][a] * Math.exp(bounded);
                }
            }
            return new MarketData(names, returns, prices, market);
        }
        String[] defaultAssetNames(int count) {
            String[] base = {
                "BankAlpha",
                "EuroEnergy",
                "TechNova",
                "MedCare",
                "GlobalRetail",
                "GreenUtilities",
                "AegeanTransport",
                "IndustrialMetals",
                "CloudSystems",
                "RealEstateFund",
                "FoodStaples",
                "InsuranceGroup",
                "TelecomCore",
                "BioScience",
                "ConsumerGrowth"
            };
            String[] names = new String[count];
            for (int i = 0; i < count; i++) {
                names[i] = base[i % base.length];
            }
            return names;
        }
        double[] assetBetas(int n) {
            double[] beta = new double[n];
            for (int i = 0; i < n; i++) {
                beta[i] = 0.55 + 0.11 * i;
            }
            if (n > 2) {
                beta[2] = 1.42;
            }
            if (n > 8) {
                beta[8] = 1.58;
            }
            if (n > 5) {
                beta[5] = 0.62;
            }
            return beta;
        }
        double[] assetAlphas(int n) {
            double[] alpha = new double[n];
            for (int i = 0; i < n; i++) {
                alpha[i] = 0.00012 + 0.00003 * Math.sin(i * 1.7);
            }
            if (n > 2) {
                alpha[2] = 0.00035;
            }
            if (n > 8) {
                alpha[8] = 0.00032;
            }
            if (n > 9) {
                alpha[9] = 0.00008;
            }
            return alpha;
        }
        double[] assetIdioVols(int n) {
            double[] vol = new double[n];
            for (int i = 0; i < n; i++) {
                vol[i] = 0.006 + 0.0014 * (i % 5);
            }
            if (n > 0) {
                vol[0] = 0.012;
            }
            if (n > 7) {
                vol[7] = 0.015;
            }
            if (n > 10) {
                vol[10] = 0.0055;
            }
            return vol;
        }
        MarketRegime regimeForDay(int day, int totalDays) {
            double ratio = day / (double) totalDays;
            if (ratio < 0.30) {
                return MarketRegime.calmExpansion();
            }
            if (ratio < 0.50) {
                return MarketRegime.stress();
            }
            if (ratio < 0.74) {
                return MarketRegime.recovery();
            }
            return MarketRegime.lateCycle();
        }
        double sectorShock(int asset, int day) {
            double periodic = Math.sin((day + asset * 13.0) / 21.0);
            if (asset == 2 || asset == 8) {
                return 0.0030 * periodic;
            }
            if (asset == 0 || asset == 11) {
                return 0.0026 * Math.sin((day + 7.0) / 17.0);
            }
            if (asset == 5 || asset == 10) {
                return 0.0012 * Math.cos(day / 29.0);
            }
            return 0.0017 * periodic;
        }
    }
    static final class MarketRegime {
        final double dailyDrift;
        final double dailyVolatility;
        final double jumpProbability;
        final double jumpMean;
        final double jumpVolatility;
        MarketRegime(double dailyDrift, double dailyVolatility, double jumpProbability, double jumpMean, double jumpVolatility) {
            this.dailyDrift = dailyDrift;
            this.dailyVolatility = dailyVolatility;
            this.jumpProbability = jumpProbability;
            this.jumpMean = jumpMean;
            this.jumpVolatility = jumpVolatility;
        }
        static MarketRegime calmExpansion() {
            return new MarketRegime(0.00045, 0.0075, 0.004, -0.012, 0.006);
        }
        static MarketRegime stress() {
            return new MarketRegime(-0.00055, 0.0185, 0.030, -0.035, 0.018);
        }
        static MarketRegime recovery() {
            return new MarketRegime(0.00065, 0.0115, 0.010, -0.018, 0.010);
        }
        static MarketRegime lateCycle() {
            return new MarketRegime(0.00025, 0.0135, 0.018, -0.026, 0.014);
        }
    }
    static final class FeatureData {
        final String[] assetNames;
        final double[][] rawFeatures;
        final double[][] normalizedFeatures;
        final String[] featureNames;
        FeatureData(String[] assetNames, double[][] rawFeatures, double[][] normalizedFeatures, String[] featureNames) {
            this.assetNames = assetNames;
            this.rawFeatures = rawFeatures;
            this.normalizedFeatures = normalizedFeatures;
            this.featureNames = featureNames;
        }
        int assets() {
            return assetNames.length;
        }
        int featureCount() {
            return featureNames.length;
        }
    }
    static final class FeatureExtractor {
        FeatureData extract(MarketData data) {
            String[] features = {
                "AnnualReturn",
                "AnnualVolatility",
                "Skewness",
                "Kurtosis",
                "VaR95",
                "CVaR95",
                "MaxDrawdown",
                "MarketBeta"
            };
            double[][] raw = new double[data.assets()][features.length];
            for (int a = 0; a < data.assets(); a++) {
                double[] r = data.assetReturns(a);
                double[] p = data.assetPrices(a);
                raw[a][0] = Stats.mean(r) * 252.0;
                raw[a][1] = Stats.standardDeviation(r) * Math.sqrt(252.0);
                raw[a][2] = Stats.skewness(r);
                raw[a][3] = Stats.kurtosis(r);
                raw[a][4] = RiskMath.valueAtRisk(r, 0.95);
                raw[a][5] = RiskMath.conditionalValueAtRisk(r, 0.95);
                raw[a][6] = RiskMath.maxDrawdown(p);
                raw[a][7] = RiskMath.beta(r, data.marketReturns);
            }
            double[][] normalized = MatrixOps.zScoreColumns(raw);
            return new FeatureData(data.assetNames, raw, normalized, features);
        }
    }
    static final class PortfolioResult {
        final String name;
        final double[] weights;
        final double[] dailyReturns;
        final double[] wealth;
        final double annualReturn;
        final double annualVolatility;
        final double sharpe;
        final double valueAtRisk;
        final double conditionalValueAtRisk;
        final double maxDrawdown;
        final double topologyPenalty;
        final double objectiveScore;
        PortfolioResult(String name, double[] weights, double[] dailyReturns, double topologyPenalty, double objectiveScore) {
            this.name = name;
            this.weights = weights;
            this.dailyReturns = dailyReturns;
            this.wealth = RiskMath.wealthCurve(dailyReturns);
            this.annualReturn = Stats.mean(dailyReturns) * 252.0;
            this.annualVolatility = Stats.standardDeviation(dailyReturns) * Math.sqrt(252.0);
            this.sharpe = RiskMath.sharpeRatio(dailyReturns, 0.02);
            this.valueAtRisk = RiskMath.valueAtRisk(dailyReturns, 0.95);
            this.conditionalValueAtRisk = RiskMath.conditionalValueAtRisk(dailyReturns, 0.95);
            this.maxDrawdown = RiskMath.maxDrawdown(wealth);
            this.topologyPenalty = topologyPenalty;
            this.objectiveScore = objectiveScore;
        }
    }
    static final class PortfolioEngine {
        final Config config;
        final Random random;
        PortfolioEngine(Config config) {
            this.config = config;
            this.random = new Random(config.seed + 1000L);
        }
        PortfolioResult equalWeightPortfolio(MarketData data) {
            double[] w = new double[data.assets()];
            Arrays.fill(w, 1.0 / data.assets());
            double[] returns = portfolioReturns(data.returns, w);
            double penalty = 0.0;
            double objective = objective(returns, penalty);
            return new PortfolioResult("Equal Weight", w, returns, penalty, objective);
        }
        List<PortfolioResult> sampleRandomPortfolios(MarketData data, FeatureData features, int count) {
            List<PortfolioResult> list = new ArrayList<>();
            double[] assetPenalty = assetTopologyPenalty(features.normalizedFeatures);
            for (int i = 0; i < count; i++) {
                double[] weights = randomLongOnlyWeights(data.assets());
                double[] returns = portfolioReturns(data.returns, weights);
                double topologyPenalty = weightedPenalty(weights, assetPenalty);
                double objective = objective(returns, topologyPenalty);
                PortfolioResult result = new PortfolioResult("Candidate " + (i + 1), weights, returns, topologyPenalty, objective);
                list.add(result);
            }
            return list;
        }
        PortfolioResult bestByCvar(List<PortfolioResult> list) {
            PortfolioResult best = null;
            for (PortfolioResult r : list) {
                if (best == null || r.conditionalValueAtRisk < best.conditionalValueAtRisk) {
                    best = r;
                }
            }
            return rename(best, "Min CVaR Portfolio");
        }
        PortfolioResult bestByTopologyAwareScore(List<PortfolioResult> list) {
            PortfolioResult best = null;
            for (PortfolioResult r : list) {
                if (best == null || r.objectiveScore < best.objectiveScore) {
                    best = r;
                }
            }
            return rename(best, "Topology Aware Portfolio");
        }
        PortfolioResult rename(PortfolioResult source, String name) {
            return new PortfolioResult(name, source.weights, source.dailyReturns, source.topologyPenalty, source.objectiveScore);
        }
        double[] randomLongOnlyWeights(int n) {
            double[] raw = new double[n];
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                double concentrationControl = 0.35 + random.nextDouble() * 2.0;
                raw[i] = -Math.log(1.0 - random.nextDouble()) / concentrationControl;
                sum += raw[i];
            }
            double cap = 0.28;
            for (int step = 0; step < 6; step++) {
                double excess = 0.0;
                double underSum = 0.0;
                for (int i = 0; i < n; i++) {
                    raw[i] = raw[i] / sum;
                }
                for (int i = 0; i < n; i++) {
                    if (raw[i] > cap) {
                        excess += raw[i] - cap;
                        raw[i] = cap;
                    } else {
                        underSum += raw[i];
                    }
                }
                if (excess <= 1.0e-12 || underSum <= 1.0e-12) {
                    break;
                }
                for (int i = 0; i < n; i++) {
                    if (raw[i] < cap) {
                        raw[i] += excess * raw[i] / underSum;
                    }
                }
                sum = Stats.sum(raw);
            }
            Stats.normalizeInPlace(raw);
            return raw;
        }
        double[] portfolioReturns(double[][] returns, double[] weights) {
            double[] r = new double[returns.length];
            for (int t = 0; t < returns.length; t++) {
                double value = 0.0;
                for (int a = 0; a < weights.length; a++) {
                    value += weights[a] * returns[t][a];
                }
                r[t] = value;
            }
            return r;
        }
        double objective(double[] returns, double topologyPenalty) {
            double mean = Stats.mean(returns) * 252.0;
            double vol = Stats.standardDeviation(returns) * Math.sqrt(252.0);
            double cvar = RiskMath.conditionalValueAtRisk(returns, 0.95) * Math.sqrt(252.0);
            double dd = RiskMath.maxDrawdown(RiskMath.wealthCurve(returns));
            double reward = Math.max(0.0, mean);
            return 0.55 * cvar + 0.25 * vol + 0.18 * dd + 0.08 * topologyPenalty - 0.10 * reward;
        }
        double[] assetTopologyPenalty(double[][] normalizedFeatures) {
            int n = normalizedFeatures.length;
            double[][] distances = MatrixOps.euclideanDistances(normalizedFeatures);
            double[] penalty = new double[n];
            for (int i = 0; i < n; i++) {
                double nearest = Double.POSITIVE_INFINITY;
                double average = 0.0;
                for (int j = 0; j < n; j++) {
                    if (i != j) {
                        nearest = Math.min(nearest, distances[i][j]);
                        average += distances[i][j];
                    }
                }
                average /= Math.max(1, n - 1);
                double isolation = nearest / (average + 1.0e-9);
                penalty[i] = Math.max(0.0, isolation);
            }
            return MatrixOps.minMaxNormalize(penalty);
        }
        double weightedPenalty(double[] weights, double[] assetPenalty) {
            double value = 0.0;
            for (int i = 0; i < weights.length; i++) {
                value += weights[i] * assetPenalty[i];
            }
            return value;
        }
    }
    static final class RiskReport {
        final String text;
        RiskReport(String text) {
            this.text = text;
        }
    }
    static final class RiskReportBuilder {
        RiskReport build(MarketData data, FeatureData features, PersistenceResult persistence, PortfolioResult equal, PortfolioResult minCvar, PortfolioResult tda, List<PortfolioResult> candidates) {
            StringBuilder sb = new StringBuilder();
            sb.append("ΤΙΤΛΟΣ\n");
            sb.append("Τοπολογική Ανάλυση Δεδομένων για Μοντελοποίηση και Μείωση Κινδύνου σε Χαρτοφυλάκια Χρηματοοικονομικών Επενδύσεων\n\n");
            sb.append("ΔΕΔΟΜΕΝΑ\n");
            sb.append("Πλήθος στοιχείων ενεργητικού: ").append(data.assets()).append('\n');
            sb.append("Ημέρες προσομοίωσης: ").append(data.days()).append('\n');
            sb.append("Υποψήφια χαρτοφυλάκια: ").append(candidates.size()).append('\n');
            sb.append("Χαρακτηριστικά για TDA: ").append(Arrays.toString(features.featureNames)).append("\n\n");
            sb.append("ΤΟΠΟΛΟΓΙΚΗ ΑΝΑΛΥΣΗ\n");
            sb.append("H0 διαστήματα: ").append(persistence.h0.size()).append('\n');
            sb.append("H1 διαστήματα: ").append(persistence.h1.size()).append('\n');
            sb.append("Μέγιστη πεπερασμένη επιμονή H0: ").append(Format.pct(persistence.maxFinitePersistence(0))).append('\n');
            sb.append("Μέγιστη πεπερασμένη επιμονή H1: ").append(Format.pct(persistence.maxFinitePersistence(1))).append('\n');
            sb.append("Τοπολογικό συμπέρασμα: ").append(persistence.interpretation()).append("\n\n");
            sb.append("ΣΥΓΚΡΙΣΗ ΧΑΡΤΟΦΥΛΑΚΙΩΝ\n");
            appendPortfolio(sb, equal);
            appendPortfolio(sb, minCvar);
            appendPortfolio(sb, tda);
            sb.append("\nΜΕΙΩΣΗ ΚΙΝΔΥΝΟΥ\n");
            double varReduction = 1.0 - tda.valueAtRisk / Math.max(equal.valueAtRisk, 1.0e-9);
            double cvarReduction = 1.0 - tda.conditionalValueAtRisk / Math.max(equal.conditionalValueAtRisk, 1.0e-9);
            double ddReduction = 1.0 - tda.maxDrawdown / Math.max(equal.maxDrawdown, 1.0e-9);
            sb.append("Μείωση VaR έναντι Equal Weight: ").append(Format.pct(varReduction)).append('\n');
            sb.append("Μείωση CVaR έναντι Equal Weight: ").append(Format.pct(cvarReduction)).append('\n');
            sb.append("Μείωση μέγιστης υποχώρησης έναντι Equal Weight: ").append(Format.pct(ddReduction)).append('\n');
            sb.append("\nΒΑΡΗ TOPOLOGY AWARE ΧΑΡΤΟΦΥΛΑΚΙΟΥ\n");
            for (int i = 0; i < data.assets(); i++) {
                sb.append(String.format("%-20s : %7s%n", data.assetNames[i], Format.pct(tda.weights[i])));
            }
            sb.append("\nΕΡΜΗΝΕΙΑ\n");
            sb.append("Ο κώδικας μετατρέπει κάθε στοιχείο ενεργητικού σε σημείο πολυδιάστατου χώρου, χρησιμοποιώντας απόδοση, μεταβλητότητα, ασυμμετρία, κύρτωση, VaR, CVaR, μέγιστη υποχώρηση και beta. ");
            sb.append("Έπειτα εφαρμόζει Vietoris-Rips persistent homology έως διάσταση 1, ώστε να ανιχνεύσει αν τα στοιχεία σχηματίζουν σταθερές ομάδες ή κύκλους κινδύνου. ");
            sb.append("Η βελτιστοποίηση δεν κοιτά μόνο τη μεταβλητότητα, αλλά και τον κίνδυνο ουράς και την τοπολογική απομόνωση των τίτλων. ");
            sb.append("Έτσι προκύπτει χαρτοφυλάκιο που αποφεύγει υπερβολική συγκέντρωση σε απομονωμένα ή επικίνδυνα σημεία του χώρου χαρακτηριστικών.\n");
            return new RiskReport(sb.toString());
        }
        void appendPortfolio(StringBuilder sb, PortfolioResult p) {
            sb.append(p.name).append('\n');
            sb.append(" Ετήσια απόδοση : ").append(Format.pct(p.annualReturn)).append('\n');
            sb.append(" Ετήσια μεταβλητότητα : ").append(Format.pct(p.annualVolatility)).append('\n');
            sb.append(" Sharpe ratio : ").append(Format.num(p.sharpe)).append('\n');
            sb.append(" VaR 95% ημερήσιο : ").append(Format.pct(p.valueAtRisk)).append('\n');
            sb.append(" CVaR 95% ημερήσιο : ").append(Format.pct(p.conditionalValueAtRisk)).append('\n');
            sb.append(" Max drawdown : ").append(Format.pct(p.maxDrawdown)).append('\n');
            sb.append(" Topology penalty : ").append(Format.num(p.topologyPenalty)).append('\n');
            sb.append(" Objective score : ").append(Format.num(p.objectiveScore)).append("\n\n");
        }
    }
    static final class TopologicalAnalyzer {
        PersistenceResult analyze(double[][] pointCloud) {
            double[][] distances = MatrixOps.euclideanDistances(pointCloud);
            List<Simplex> simplices = buildVietorisRipsSimplices(distances);
            Collections.sort(simplices);
            Map<String, Integer> indexByKey = new LinkedHashMap<>();
            for (int i = 0; i < simplices.size(); i++) {
                indexByKey.put(simplices.get(i).key, i);
            }
            boolean[] creator = new boolean[simplices.size()];
            boolean[] pairedAsBirth = new boolean[simplices.size()];
            Map<Integer, TreeSet<Integer>> reducedColumns = new LinkedHashMap<>();
            Map<Integer, Integer> pivotColumnByLow = new LinkedHashMap<>();
            List<PersistenceInterval> h0 = new ArrayList<>();
            List<PersistenceInterval> h1 = new ArrayList<>();
            for (int j = 0; j < simplices.size(); j++) {
                TreeSet<Integer> column = boundaryColumn(simplices.get(j), indexByKey);
                while (!column.isEmpty()) {
                    int low = column.last();
                    Integer previousColumnIndex = pivotColumnByLow.get(low);
                    if (previousColumnIndex == null) {
                        break;
                    }
                    column = xor(column, reducedColumns.get(previousColumnIndex));
                }
                if (column.isEmpty()) {
                    reducedColumns.put(j, column);
                    if (simplices.get(j).dimension <= 1) {
                        creator[j] = true;
                    }
                } else {
                    int low = column.last();
                    pivotColumnByLow.put(low, j);
                    reducedColumns.put(j, column);
                    pairedAsBirth[low] = true;
                    Simplex birth = simplices.get(low);
                    Simplex death = simplices.get(j);
                    PersistenceInterval interval = new PersistenceInterval(birth.dimension, birth.filtration, death.filtration, false, birth.label(), death.label());
                    if (birth.dimension == 0) {
                        h0.add(interval);
                    }
                    if (birth.dimension == 1) {
                        h1.add(interval);
                    }
                }
            }
            double infinity = maxFiltration(simplices) * 1.18 + 0.01;
            for (int i = 0; i < simplices.size(); i++) {
                if (creator[i] && !pairedAsBirth[i]) {
                    Simplex s = simplices.get(i);
                    PersistenceInterval interval = new PersistenceInterval(s.dimension, s.filtration, infinity, true, s.label(), "infinity");
                    if (s.dimension == 0) {
                        h0.add(interval);
                    }
                    if (s.dimension == 1) {
                        h1.add(interval);
                    }
                }
            }
            h0.sort(Comparator.comparingDouble(PersistenceInterval::persistence));
            h1.sort(Comparator.comparingDouble(PersistenceInterval::persistence));
            return new PersistenceResult(h0, h1, infinity);
        }
        List<Simplex> buildVietorisRipsSimplices(double[][] distances) {
            int n = distances.length;
            List<Simplex> simplices = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                simplices.add(Simplex.vertex(i));
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    simplices.add(Simplex.edge(i, j, distances[i][j]));
                }
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    for (int k = j + 1; k < n; k++) {
                        double f = Math.max(distances[i][j], Math.max(distances[i][k], distances[j][k]));
                        simplices.add(Simplex.triangle(i, j, k, f));
                    }
                }
            }
            return simplices;
        }
        TreeSet<Integer> boundaryColumn(Simplex simplex, Map<String, Integer> indexByKey) {
            TreeSet<Integer> column = new TreeSet<>();
            if (simplex.dimension == 0) {
                return column;
            }
            if (simplex.dimension == 1) {
                column.add(indexByKey.get(Simplex.vertex(simplex.vertices[0]).key));
                column.add(indexByKey.get(Simplex.vertex(simplex.vertices[1]).key));
                return column;
            }
            if (simplex.dimension == 2) {
                int a = simplex.vertices[0];
                int b = simplex.vertices[1];
                int c = simplex.vertices[2];
                column.add(indexByKey.get(Simplex.edgeKey(a, b)));
                column.add(indexByKey.get(Simplex.edgeKey(a, c)));
                column.add(indexByKey.get(Simplex.edgeKey(b, c)));
                return column;
            }
            return column;
        }
        TreeSet<Integer> xor(TreeSet<Integer> a, TreeSet<Integer> b) {
            TreeSet<Integer> result = new TreeSet<>(a);
            for (Integer value : b) {
                if (result.contains(value)) {
                    result.remove(value);
                } else {
                    result.add(value);
                }
            }
            return result;
        }
        double maxFiltration(List<Simplex> simplices) {
            double max = 0.0;
            for (Simplex s : simplices) {
                max = Math.max(max, s.filtration);
            }
            return max;
        }
    }
    static final class Simplex implements Comparable<Simplex> {
        final int[] vertices;
        final int dimension;
        final double filtration;
        final String key;
        Simplex(int[] vertices, int dimension, double filtration) {
            this.vertices = vertices;
            this.dimension = dimension;
            this.filtration = filtration;
            this.key = buildKey(vertices);
        }
        static Simplex vertex(int i) {
            return new Simplex(new int[]{i}, 0, 0.0);
        }
        static Simplex edge(int i, int j, double filtration) {
            return new Simplex(new int[]{Math.min(i, j), Math.max(i, j)}, 1, filtration);
        }
        static Simplex triangle(int i, int j, int k, double filtration) {
            int[] v = {i, j, k};
            Arrays.sort(v);
            return new Simplex(v, 2, filtration);
        }
        static String edgeKey(int i, int j) {
            int a = Math.min(i, j);
            int b = Math.max(i, j);
            return buildKey(new int[]{a, b});
        }
        static String buildKey(int[] vertices) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vertices.length; i++) {
                if (i > 0) {
                    sb.append('-');
                }
                sb.append(vertices[i]);
            }
            return sb.toString();
        }
        String label() {
            if (dimension == 0) {
                return "v" + vertices[0];
            }
            if (dimension == 1) {
                return "e" + vertices[0] + "-" + vertices[1];
            }
            return "t" + vertices[0] + "-" + vertices[1] + "-" + vertices[2];
        }
        @Override
        public int compareTo(Simplex other) {
            int c = Double.compare(this.filtration, other.filtration);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(this.dimension, other.dimension);
            if (c != 0) {
                return c;
            }
            return this.key.compareTo(other.key);
        }
    }
    static final class PersistenceInterval {
        final int dimension;
        final double birth;
        final double death;
        final boolean essential;
        final String birthSimplex;
        final String deathSimplex;
        PersistenceInterval(int dimension, double birth, double death, boolean essential, String birthSimplex, String deathSimplex) {
            this.dimension = dimension;
            this.birth = birth;
            this.death = death;
            this.essential = essential;
            this.birthSimplex = birthSimplex;
            this.deathSimplex = deathSimplex;
        }
        double persistence() {
            return Math.max(0.0, death - birth);
        }
    }
    static final class PersistenceResult {
        final List<PersistenceInterval> h0;
        final List<PersistenceInterval> h1;
        final double infinityCap;
        PersistenceResult(List<PersistenceInterval> h0, List<PersistenceInterval> h1, double infinityCap) {
            this.h0 = h0;
            this.h1 = h1;
            this.infinityCap = infinityCap;
        }
        double maxFinitePersistence(int dimension) {
            List<PersistenceInterval> list = dimension == 0 ? h0 : h1;
            double max = 0.0;
            for (PersistenceInterval interval : list) {
                if (!interval.essential) {
                    max = Math.max(max, interval.persistence());
                }
            }
            return max;
        }
        String interpretation() {
            double h0Max = maxFinitePersistence(0);
            double h1Max = maxFinitePersistence(1);
            if (h1Max > h0Max * 0.65 && h1Max > 0.25) {
                return "υπάρχει πιθανός κύκλος κινδύνου μεταξύ ομάδων τίτλων, άρα απαιτείται προσοχή στη συσχέτιση και στη συγκέντρωση";
            }
            if (h0Max > 0.65) {
                return "υπάρχουν έντονα διαχωρισμένες ομάδες τίτλων, άρα η διαφοροποίηση πρέπει να μοιραστεί μεταξύ ομάδων";
            }
            return "η τοπολογική δομή είναι σχετικά συμπαγής, χωρίς ακραία μόνιμα κενά ή έντονη απομόνωση";
        }
    }
    static final class Stats {
        static double sum(double[] v) {
            double s = 0.0;
            for (double x : v) {
                s += x;
            }
            return s;
        }
        static double mean(double[] v) {
            if (v.length == 0) {
                return 0.0;
            }
            return sum(v) / v.length;
        }
        static double variance(double[] v) {
            if (v.length < 2) {
                return 0.0;
            }
            double mean = mean(v);
            double s = 0.0;
            for (double x : v) {
                double d = x - mean;
                s += d * d;
            }
            return s / (v.length - 1);
        }
        static double standardDeviation(double[] v) {
            return Math.sqrt(Math.max(0.0, variance(v)));
        }
        static double min(double[] v) {
            double m = Double.POSITIVE_INFINITY;
            for (double x : v) {
                m = Math.min(m, x);
            }
            return m;
        }
        static double max(double[] v) {
            double m = Double.NEGATIVE_INFINITY;
            for (double x : v) {
                m = Math.max(m, x);
            }
            return m;
        }
        static double percentile(double[] values, double p) {
            double[] copy = Arrays.copyOf(values, values.length);
            Arrays.sort(copy);
            if (copy.length == 0) {
                return 0.0;
            }
            double pos = p * (copy.length - 1);
            int lower = (int) Math.floor(pos);
            int upper = (int) Math.ceil(pos);
            if (lower == upper) {
                return copy[lower];
            }
            double weight = pos - lower;
            return copy[lower] * (1.0 - weight) + copy[upper] * weight;
        }
        static double covariance(double[] x, double[] y) {
            int n = Math.min(x.length, y.length);
            if (n < 2) {
                return 0.0;
            }
            double mx = mean(Arrays.copyOf(x, n));
            double my = mean(Arrays.copyOf(y, n));
            double s = 0.0;
            for (int i = 0; i < n; i++) {
                s += (x[i] - mx) * (y[i] - my);
            }
            return s / (n - 1);
        }
        static double correlation(double[] x, double[] y) {
            double sx = standardDeviation(x);
            double sy = standardDeviation(y);
            if (sx <= 1.0e-12 || sy <= 1.0e-12) {
                return 0.0;
            }
            return covariance(x, y) / (sx * sy);
        }
        static double skewness(double[] v) {
            double mean = mean(v);
            double sd = standardDeviation(v);
            if (sd <= 1.0e-12) {
                return 0.0;
            }
            double s = 0.0;
            for (double x : v) {
                s += Math.pow((x - mean) / sd, 3.0);
            }
            return s / v.length;
        }
        static double kurtosis(double[] v) {
            double mean = mean(v);
            double sd = standardDeviation(v);
            if (sd <= 1.0e-12) {
                return 0.0;
            }
            double s = 0.0;
            for (double x : v) {
                s += Math.pow((x - mean) / sd, 4.0);
            }
            return s / v.length - 3.0;
        }
        static void normalizeInPlace(double[] v) {
            double s = sum(v);
            if (s <= 1.0e-12) {
                Arrays.fill(v, 1.0 / v.length);
                return;
            }
            for (int i = 0; i < v.length; i++) {
                v[i] /= s;
            }
        }
        static double[] cumulativeSum(double[] v) {
            double[] c = new double[v.length];
            double s = 0.0;
            for (int i = 0; i < v.length; i++) {
                s += v[i];
                c[i] = s;
            }
            return c;
        }
        static double[] rollingMean(double[] v, int window) {
            double[] out = new double[v.length];
            double sum = 0.0;
            for (int i = 0; i < v.length; i++) {
                sum += v[i];
                if (i >= window) {
                    sum -= v[i - window];
                }
                int denom = Math.min(i + 1, window);
                out[i] = sum / denom;
            }
            return out;
        }
        static double[] rollingVolatility(double[] v, int window) {
            double[] out = new double[v.length];
            for (int i = 0; i < v.length; i++) {
                int start = Math.max(0, i - window + 1);
                double[] slice = Arrays.copyOfRange(v, start, i + 1);
                out[i] = standardDeviation(slice);
            }
            return out;
        }
        static double median(double[] v) {
            return percentile(v, 0.50);
        }
        static double meanAbsoluteDeviation(double[] v) {
            double m = mean(v);
            double s = 0.0;
            for (double x : v) {
                s += Math.abs(x - m);
            }
            return s / Math.max(1, v.length);
        }
        static double downsideDeviation(double[] v, double threshold) {
            double s = 0.0;
            int c = 0;
            for (double x : v) {
                if (x < threshold) {
                    double d = x - threshold;
                    s += d * d;
                    c++;
                }
            }
            if (c == 0) {
                return 0.0;
            }
            return Math.sqrt(s / c);
        }
        static double geometricMeanReturn(double[] dailyReturns) {
            double product = 1.0;
            for (double r : dailyReturns) {
                product *= 1.0 + r;
            }
            return Math.pow(product, 252.0 / dailyReturns.length) - 1.0;
        }
        static double semiVariance(double[] v) {
            double m = mean(v);
            double s = 0.0;
            int count = 0;
            for (double x : v) {
                if (x < m) {
                    double d = x - m;
                    s += d * d;
                    count++;
                }
            }
            if (count <= 1) {
                return 0.0;
            }
            return s / (count - 1);
        }
        static double annualizedVolatility(double[] returns) {
            return standardDeviation(returns) * Math.sqrt(252.0);
        }
        static double annualizedArithmeticReturn(double[] returns) {
            return mean(returns) * 252.0;
        }
        static double trackingError(double[] portfolio, double[] benchmark) {
            int n = Math.min(portfolio.length, benchmark.length);
            double[] diff = new double[n];
            for (int i = 0; i < n; i++) {
                diff[i] = portfolio[i] - benchmark[i];
            }
            return standardDeviation(diff) * Math.sqrt(252.0);
        }
        static double informationRatio(double[] portfolio, double[] benchmark) {
            int n = Math.min(portfolio.length, benchmark.length);
            double[] diff = new double[n];
            for (int i = 0; i < n; i++) {
                diff[i] = portfolio[i] - benchmark[i];
            }
            double te = standardDeviation(diff) * Math.sqrt(252.0);
            if (te <= 1.0e-12) {
                return 0.0;
            }
            return mean(diff) * 252.0 / te;
        }
        static double maxConsecutiveLoss(double[] returns) {
            double loss = 0.0;
            double current = 0.0;
            for (double r : returns) {
                if (r < 0.0) {
                    current += -r;
                    loss = Math.max(loss, current);
                } else {
                    current = 0.0;
                }
            }
            return loss;
        }
        static double winRate(double[] returns) {
            int wins = 0;
            for (double r : returns) {
                if (r > 0.0) {
                    wins++;
                }
            }
            return wins / (double) Math.max(1, returns.length);
        }
    }
    static final class RiskMath {
        static double valueAtRisk(double[] returns, double confidence) {
            double percentile = 1.0 - confidence;
            double q = Stats.percentile(returns, percentile);
            return Math.max(0.0, -q);
        }
        static double conditionalValueAtRisk(double[] returns, double confidence) {
            double var = valueAtRisk(returns, confidence);
            double threshold = -var;
            double sum = 0.0;
            int count = 0;
            for (double r : returns) {
                if (r <= threshold + 1.0e-12) {
                    sum += -r;
                    count++;
                }
            }
            if (count == 0) {
                return var;
            }
            return Math.max(var, sum / count);
        }
        static double[] wealthCurve(double[] returns) {
            double[] wealth = new double[returns.length + 1];
            wealth[0] = 1.0;
            for (int i = 0; i < returns.length; i++) {
                wealth[i + 1] = wealth[i] * (1.0 + returns[i]);
            }
            return wealth;
        }
        static double maxDrawdown(double[] wealthOrPrices) {
            double peak = wealthOrPrices.length == 0 ? 0.0 : wealthOrPrices[0];
            double maxDrawdown = 0.0;
            for (double value : wealthOrPrices) {
                peak = Math.max(peak, value);
                if (peak > 0.0) {
                    maxDrawdown = Math.max(maxDrawdown, (peak - value) / peak);
                }
            }
            return maxDrawdown;
        }
        static double sharpeRatio(double[] returns, double annualRiskFreeRate) {
            double dailyRiskFree = annualRiskFreeRate / 252.0;
            double[] excess = new double[returns.length];
            for (int i = 0; i < returns.length; i++) {
                excess[i] = returns[i] - dailyRiskFree;
            }
            double vol = Stats.standardDeviation(excess) * Math.sqrt(252.0);
            if (vol <= 1.0e-12) {
                return 0.0;
            }
            return Stats.mean(excess) * 252.0 / vol;
        }
        static double beta(double[] asset, double[] market) {
            double var = Stats.variance(market);
            if (var <= 1.0e-12) {
                return 0.0;
            }
            return Stats.covariance(asset, market) / var;
        }
        static double sortinoRatio(double[] returns, double annualRiskFreeRate) {
            double dailyRiskFree = annualRiskFreeRate / 252.0;
            double meanExcess = Stats.mean(returns) - dailyRiskFree;
            double downside = Stats.downsideDeviation(returns, dailyRiskFree) * Math.sqrt(252.0);
            if (downside <= 1.0e-12) {
                return 0.0;
            }
            return meanExcess * 252.0 / downside;
        }
        static double calmarRatio(double[] returns) {
            double annual = Stats.annualizedArithmeticReturn(returns);
            double dd = maxDrawdown(wealthCurve(returns));
            if (dd <= 1.0e-12) {
                return 0.0;
            }
            return annual / dd;
        }
        static double expectedShortfallContribution(double[] assetReturns, double[] portfolioReturns, double confidence) {
            double var = valueAtRisk(portfolioReturns, confidence);
            double threshold = -var;
            double sum = 0.0;
            int count = 0;
            for (int i = 0; i < portfolioReturns.length; i++) {
                if (portfolioReturns[i] <= threshold) {
                    sum += -assetReturns[i];
                    count++;
                }
            }
            if (count == 0) {
                return 0.0;
            }
            return sum / count;
        }
        static double herfindahl(double[] weights) {
            double h = 0.0;
            for (double w : weights) {
                h += w * w;
            }
            return h;
        }
        static double effectiveNumberOfBets(double[] weights) {
            double h = herfindahl(weights);
            if (h <= 1.0e-12) {
                return 0.0;
            }
            return 1.0 / h;
        }
        static double turnover(double[] previousWeights, double[] nextWeights) {
            int n = Math.min(previousWeights.length, nextWeights.length);
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                sum += Math.abs(nextWeights[i] - previousWeights[i]);
            }
            return 0.5 * sum;
        }
        static double portfolioVariance(double[] weights, double[][] covariance) {
            double v = 0.0;
            for (int i = 0; i < weights.length; i++) {
                for (int j = 0; j < weights.length; j++) {
                    v += weights[i] * weights[j] * covariance[i][j];
                }
            }
            return v;
        }
        static double marginalRiskContribution(double[] weights, double[][] covariance, int asset) {
            double portfolioVariance = portfolioVariance(weights, covariance);
            if (portfolioVariance <= 1.0e-12) {
                return 0.0;
            }
            double sigma = Math.sqrt(portfolioVariance);
            double covTerm = 0.0;
            for (int j = 0; j < weights.length; j++) {
                covTerm += covariance[asset][j] * weights[j];
            }
            return covTerm / sigma;
        }
        static double componentRiskContribution(double[] weights, double[][] covariance, int asset) {
            double mrc = marginalRiskContribution(weights, covariance, asset);
            return weights[asset] * mrc;
        }
        static double diversificationRatio(double[] weights, double[][] covariance) {
            double weightedStandalone = 0.0;
            for (int i = 0; i < weights.length; i++) {
                weightedStandalone += weights[i] * Math.sqrt(Math.max(0.0, covariance[i][i]));
            }
            double portVol = Math.sqrt(Math.max(0.0, portfolioVariance(weights, covariance)));
            if (portVol <= 1.0e-12) {
                return 0.0;
            }
            return weightedStandalone / portVol;
        }
    }
    static final class MatrixOps {
        static double[][] covariance(double[][] observationsByAsset) {
            int rows = observationsByAsset.length;
            int cols = observationsByAsset[0].length;
            double[][] cov = new double[cols][cols];
            for (int i = 0; i < cols; i++) {
                double[] xi = column(observationsByAsset, i);
                for (int j = i; j < cols; j++) {
                    double[] xj = column(observationsByAsset, j);
                    cov[i][j] = Stats.covariance(xi, xj);
                    cov[j][i] = cov[i][j];
                }
            }
            return cov;
        }
        static double[][] correlation(double[][] observationsByAsset) {
            int cols = observationsByAsset[0].length;
            double[][] corr = new double[cols][cols];
            for (int i = 0; i < cols; i++) {
                double[] xi = column(observationsByAsset, i);
                for (int j = i; j < cols; j++) {
                    double[] xj = column(observationsByAsset, j);
                    corr[i][j] = Stats.correlation(xi, xj);
                    corr[j][i] = corr[i][j];
                }
            }
            return corr;
        }
        static double[] column(double[][] matrix, int col) {
            double[] out = new double[matrix.length];
            for (int r = 0; r < matrix.length; r++) {
                out[r] = matrix[r][col];
            }
            return out;
        }
        static double[][] zScoreColumns(double[][] matrix) {
            int rows = matrix.length;
            int cols = matrix[0].length;
            double[][] out = new double[rows][cols];
            for (int c = 0; c < cols; c++) {
                double[] col = column(matrix, c);
                double mean = Stats.mean(col);
                double sd = Stats.standardDeviation(col);
                if (sd <= 1.0e-12) {
                    sd = 1.0;
                }
                for (int r = 0; r < rows; r++) {
                    out[r][c] = (matrix[r][c] - mean) / sd;
                }
            }
            return out;
        }
        static double[][] euclideanDistances(double[][] points) {
            int n = points.length;
            double[][] d = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double value = euclidean(points[i], points[j]);
                    d[i][j] = value;
                    d[j][i] = value;
                }
            }
            return d;
        }
        static double euclidean(double[] a, double[] b) {
            int n = Math.min(a.length, b.length);
            double s = 0.0;
            for (int i = 0; i < n; i++) {
                double diff = a[i] - b[i];
                s += diff * diff;
            }
            return Math.sqrt(s);
        }
        static double[] minMaxNormalize(double[] values) {
            double min = Stats.min(values);
            double max = Stats.max(values);
            double[] out = new double[values.length];
            double range = max - min;
            if (range <= 1.0e-12) {
                Arrays.fill(out, 0.0);
                return out;
            }
            for (int i = 0; i < values.length; i++) {
                out[i] = (values[i] - min) / range;
            }
            return out;
        }
        static double[][] transpose(double[][] matrix) {
            int rows = matrix.length;
            int cols = matrix[0].length;
            double[][] out = new double[cols][rows];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    out[c][r] = matrix[r][c];
                }
            }
            return out;
        }
        static double[] multiply(double[][] matrix, double[] vector) {
            double[] out = new double[matrix.length];
            for (int r = 0; r < matrix.length; r++) {
                double s = 0.0;
                for (int c = 0; c < vector.length; c++) {
                    s += matrix[r][c] * vector[c];
                }
                out[r] = s;
            }
            return out;
        }
        static double dot(double[] a, double[] b) {
            int n = Math.min(a.length, b.length);
            double s = 0.0;
            for (int i = 0; i < n; i++) {
                s += a[i] * b[i];
            }
            return s;
        }
        static double[][] copy(double[][] matrix) {
            double[][] out = new double[matrix.length][];
            for (int i = 0; i < matrix.length; i++) {
                out[i] = Arrays.copyOf(matrix[i], matrix[i].length);
            }
            return out;
        }
        static double trace(double[][] matrix) {
            double s = 0.0;
            int n = Math.min(matrix.length, matrix[0].length);
            for (int i = 0; i < n; i++) {
                s += matrix[i][i];
            }
            return s;
        }
        static double frobeniusNorm(double[][] matrix) {
            double s = 0.0;
            for (double[] row : matrix) {
                for (double x : row) {
                    s += x * x;
                }
            }
            return Math.sqrt(s);
        }
        static double averageOffDiagonal(double[][] matrix) {
            double s = 0.0;
            int count = 0;
            for (int i = 0; i < matrix.length; i++) {
                for (int j = 0; j < matrix[i].length; j++) {
                    if (i != j) {
                        s += matrix[i][j];
                        count++;
                    }
                }
            }
            return s / Math.max(1, count);
        }
        static double maxOffDiagonal(double[][] matrix) {
            double m = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < matrix.length; i++) {
                for (int j = 0; j < matrix[i].length; j++) {
                    if (i != j) {
                        m = Math.max(m, matrix[i][j]);
                    }
                }
            }
            return m;
        }
        static double minOffDiagonal(double[][] matrix) {
            double m = Double.POSITIVE_INFINITY;
            for (int i = 0; i < matrix.length; i++) {
                for (int j = 0; j < matrix[i].length; j++) {
                    if (i != j) {
                        m = Math.min(m, matrix[i][j]);
                    }
                }
            }
            return m;
        }
    }
    static abstract class ChartPanel extends JPanel {
        final Font titleFont;
        final Font labelFont;
        final Font smallFont;
        final Color axisColor;
        final Color gridColor;
        final Color textColor;
        final Color tdaColor;
        final Color equalColor;
        final Color safeColor;
        final Color warningColor;
        final Color badColor;
        ChartPanel() {
            this.titleFont = new Font("SansSerif", Font.BOLD, 20);
            this.labelFont = new Font("SansSerif", Font.PLAIN, 13);
            this.smallFont = new Font("SansSerif", Font.PLAIN, 11);
            this.axisColor = new Color(40, 40, 40);
            this.gridColor = new Color(232, 232, 232);
            this.textColor = new Color(30, 30, 30);
            this.tdaColor = new Color(0, 135, 80);
            this.equalColor = new Color(50, 90, 190);
            this.safeColor = new Color(80, 170, 95);
            this.warningColor = new Color(235, 165, 65);
            this.badColor = new Color(210, 85, 75);
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            draw(g2);
            g2.dispose();
        }
        abstract void draw(Graphics2D g2);
        Rectangle2D mainArea() {
            return new Rectangle2D.Double(90, 95, Math.max(360, getWidth() - 170), Math.max(250, getHeight() - 180));
        }
        void title(Graphics2D g2, String title, String subtitle) {
            g2.setColor(textColor);
            g2.setFont(titleFont);
            g2.drawString(title, 24, 34);
            g2.setFont(labelFont);
            g2.setColor(new Color(85, 85, 85));
            g2.drawString(subtitle, 24, 58);
        }
        void note(Graphics2D g2, String text, int x, int y) {
            g2.setFont(labelFont);
            g2.setColor(new Color(65, 65, 65));
            int w = g2.getFontMetrics().stringWidth(text) + 18;
            g2.setColor(new Color(248, 248, 248));
            g2.fill(new Rectangle2D.Double(x - 8, y - 17, w, 24));
            g2.setColor(new Color(200, 200, 200));
            g2.draw(new Rectangle2D.Double(x - 8, y - 17, w, 24));
            g2.setColor(new Color(65, 65, 65));
            g2.drawString(text, x, y);
        }
        void axes(Graphics2D g2, Rectangle2D area, String xLabel, String yLabel) {
            g2.setStroke(new BasicStroke(1.6f));
            g2.setColor(axisColor);
            g2.draw(new Line2D.Double(area.getMinX(), area.getMaxY(), area.getMaxX(), area.getMaxY()));
            g2.draw(new Line2D.Double(area.getMinX(), area.getMinY(), area.getMinX(), area.getMaxY()));
            g2.setFont(labelFont);
            drawCentered(g2, xLabel, (int) area.getCenterX(), (int) area.getMaxY() + 42);
            Graphics2D r = (Graphics2D) g2.create();
            r.rotate(-Math.PI / 2.0);
            r.drawString(yLabel, (int) (-area.getCenterY() - 60), 28);
            r.dispose();
        }
        void grid(Graphics2D g2, Rectangle2D area) {
            g2.setColor(gridColor);
            g2.setStroke(new BasicStroke(1.0f));
            for (int i = 1; i <= 4; i++) {
                double x = area.getMinX() + i * area.getWidth() / 5.0;
                double y = area.getMinY() + i * area.getHeight() / 5.0;
                g2.draw(new Line2D.Double(x, area.getMinY(), x, area.getMaxY()));
                g2.draw(new Line2D.Double(area.getMinX(), y, area.getMaxX(), y));
            }
        }
        int sx(Rectangle2D area, double x, double min, double max) {
            if (max <= min) {
                return (int) area.getCenterX();
            }
            double r = (x - min) / (max - min);
            return (int) Math.round(area.getMinX() + clamp(r, 0.0, 1.0) * area.getWidth());
        }
        int sy(Rectangle2D area, double y, double min, double max) {
            if (max <= min) {
                return (int) area.getCenterY();
            }
            double r = (y - min) / (max - min);
            return (int) Math.round(area.getMaxY() - clamp(r, 0.0, 1.0) * area.getHeight());
        }
        double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
        void drawCentered(Graphics2D g2, String text, int x, int y) {
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, x - fm.stringWidth(text) / 2, y);
        }
        void legend(Graphics2D g2, String[] labels, Color[] colors, int x, int y) {
            g2.setFont(labelFont);
            for (int i = 0; i < labels.length; i++) {
                int yy = y + i * 24;
                g2.setColor(colors[i]);
                g2.fill(new Rectangle2D.Double(x, yy - 12, 18, 14));
                g2.setColor(Color.BLACK);
                g2.draw(new Rectangle2D.Double(x, yy - 12, 18, 14));
                g2.drawString(labels[i], x + 26, yy);
            }
        }
        void tickLabels(Graphics2D g2, Rectangle2D area, double minY, double maxY) {
            g2.setFont(smallFont);
            g2.setColor(new Color(70, 70, 70));
            for (int i = 0; i <= 4; i++) {
                double value = minY + i * (maxY - minY) / 4.0;
                int y = sy(area, value, minY, maxY);
                g2.drawString(Format.num(value), 22, y + 4);
            }
        }
        String shortName(String s) {
            if (s.length() <= 12) {
                return s;
            }
            return s.substring(0, 12);
        }
        Color simpleColor(int index) {
            Color[] colors = {
                new Color(50, 90, 190),
                new Color(0, 135, 80),
                new Color(210, 85, 75),
                new Color(130, 90, 170),
                new Color(230, 150, 45),
                new Color(70, 145, 160)
            };
            return colors[index % colors.length];
        }
        Color riskColor(double x, double min, double max) {
            double r = clamp((x - min) / Math.max(1.0e-9, max - min), 0.0, 1.0);
            if (r < 0.34) {
                return safeColor;
            }
            if (r < 0.67) {
                return warningColor;
            }
            return badColor;
        }
    }
    static final class PriceChartPanel extends ChartPanel {
        final MarketData data;
        PriceChartPanel(MarketData data) {
            this.data = data;
        }
        @Override
        void draw(Graphics2D g2) {
            title(g2, "1. Απλή εικόνα τιμών", "Εμφανίζονται μόνο 3 ενδεικτικά assets, κανονικοποιημένα στην αρχική τιμή 100");
            Rectangle2D area = mainArea();
            grid(g2, area);
            axes(g2, area, "χρόνος", "τιμή με βάση το 100");
            int[] shown = chooseAssets();
            double min = 75.0;
            double max = 135.0;
            for (int asset : shown) {
                for (int t = 0; t < data.prices.length; t++) {
                    double value = normalizedPrice(t, asset);
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
            min = Math.floor(min / 10.0) * 10.0;
            max = Math.ceil(max / 10.0) * 10.0;
            String[] labels = new String[shown.length];
            Color[] colors = new Color[shown.length];
            for (int i = 0; i < shown.length; i++) {
                int asset = shown[i];
                labels[i] = shortName(data.assetNames[asset]);
                colors[i] = simpleColor(i);
                drawOnePriceLine(g2, area, asset, min, max, colors[i]);
            }
            tickLabels(g2, area, min, max);
            legend(g2, labels, colors, (int) area.getMaxX() - 115, (int) area.getMinY() + 22);
            note(g2, "Πάνω από 100 = κέρδος, κάτω από 100 = ζημία", (int) area.getMinX() + 10, (int) area.getMaxY() + 68);
        }
        int[] chooseAssets() {
            int n = data.assets();
            if (n <= 3) {
                int[] a = new int[n];
                for (int i = 0; i < n; i++) {
                    a[i] = i;
                }
                return a;
            }
            return new int[] {0, n / 2, n - 1};
        }
        double normalizedPrice(int day, int asset) {
            return 100.0 * data.prices[day][asset] / Math.max(1.0e-9, data.prices[0][asset]);
        }
        void drawOnePriceLine(Graphics2D g2, Rectangle2D area, int asset, double min, double max, Color color) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(3.0f));
            for (int t = 1; t < data.prices.length; t++) {
                int x1 = sx(area, t - 1, 0, data.prices.length - 1);
                int y1 = sy(area, normalizedPrice(t - 1, asset), min, max);
                int x2 = sx(area, t, 0, data.prices.length - 1);
                int y2 = sy(area, normalizedPrice(t, asset), min, max);
                g2.draw(new Line2D.Double(x1, y1, x2, y2));
            }
        }
    }
    static final class CorrelationHeatmapPanel extends ChartPanel {
        final MarketData data;
        CorrelationHeatmapPanel(MarketData data) {
            this.data = data;
        }
        @Override
        void draw(Graphics2D g2) {
            title(g2, "2. Απλή εικόνα συσχέτισης", "Τα 12 assets ομαδοποιούνται σε 4 ομάδες για να μη γεμίζει το γράφημα με πολλά μικρά κουτάκια");
            String[] groupNames = {"Ομάδα Α", "Ομάδα Β", "Ομάδα Γ", "Ομάδα Δ"};
            int groups = groupNames.length;
            double[][] grouped = groupedCorrelations(groups);
            int size = Math.min(getWidth() - 240, getHeight() - 185);
            Rectangle2D area = new Rectangle2D.Double(155, 95, size, size);
            double cell = area.getWidth() / groups;
            for (int r = 0; r < groups; r++) {
                for (int c = 0; c < groups; c++) {
                    double corr = grouped[r][c];
                    Rectangle2D box = new Rectangle2D.Double(area.getMinX() + c * cell, area.getMinY() + r * cell, cell, cell);
                    g2.setColor(colorForCorrelation(corr));
                    g2.fill(box);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(3.0f));
                    g2.draw(box);
                    g2.setColor(Color.BLACK);
                    g2.setFont(labelFont);
                    drawCentered(g2, Format.num(corr), (int) box.getCenterX(), (int) box.getCenterY() - 6);
                    g2.setFont(smallFont);
                    drawCentered(g2, corrText(corr), (int) box.getCenterX(), (int) box.getCenterY() + 15);
                }
            }
            g2.setFont(labelFont);
            g2.setColor(Color.BLACK);
            for (int i = 0; i < groups; i++) {
                drawCentered(g2, groupNames[i], (int) (area.getMinX() + i * cell + cell / 2.0), (int) area.getMaxY() + 28);
                g2.drawString(groupNames[i], 70, (int) (area.getMinY() + i * cell + cell / 2.0 + 5));
            }
            legend(g2,
            new String[] {"χαμηλή", "μέτρια", "υψηλή"},
            new Color[] {safeColor, warningColor, badColor},
            (int) area.getMaxX() + 35,
            (int) area.getMinY() + 45);
            note(g2, "Κόκκινο = οι τίτλοι κινούνται πολύ μαζί, άρα μειώνεται η διαφοροποίηση", (int) area.getMinX(), (int) area.getMaxY() + 68);
        }
        double[][] groupedCorrelations(int groups) {
            double[][] out = new double[groups][groups];
            int[] sizes = new int[groups * groups];
            for (int i = 0; i < data.assets(); i++) {
                int gi = Math.min(groups - 1, i * groups / data.assets());
                for (int j = 0; j < data.assets(); j++) {
                    int gj = Math.min(groups - 1, j * groups / data.assets());
                    out[gi][gj] += data.correlation[i][j];
                    sizes[gi * groups + gj]++;
                }
            }
            for (int i = 0; i < groups; i++) {
                for (int j = 0; j < groups; j++) {
                    out[i][j] /= Math.max(1, sizes[i * groups + j]);
                }
            }
            return out;
        }
        Color colorForCorrelation(double corr) {
            double c = Math.abs(corr);
            if (c < 0.35) {
                return safeColor;
            }
            if (c < 0.70) {
                return warningColor;
            }
            return badColor;
        }
        String corrText(double corr) {
            double c = Math.abs(corr);
            if (c < 0.35) {
                return "χαμηλή";
            }
            if (c < 0.70) {
                return "μέτρια";
            }
            return "υψηλή";
        }
    }
    static final class RiskReturnScatterPanel extends ChartPanel {
        final List<PortfolioResult> sampled;
        final PortfolioResult equal;
        final PortfolioResult minCvar;
        final PortfolioResult tda;
        RiskReturnScatterPanel(List<PortfolioResult> sampled, PortfolioResult equal, PortfolioResult minCvar, PortfolioResult tda) {
            this.sampled = sampled;
            this.equal = equal;
            this.minCvar = minCvar;
            this.tda = tda;
        }
        @Override
        void draw(Graphics2D g2) {
            title(g2, "3. Απόδοση και κίνδυνος", "Το γράφημα κρατά μόνο τα 3 τελικά χαρτοφυλάκια για να είναι καθαρό");
            Rectangle2D area = mainArea();
            grid(g2, area);
            axes(g2, area, "κίνδυνος / volatility", "ετήσια απόδοση");
            PortfolioResult[] portfolios = {equal, minCvar, tda};
            double minRisk = Double.POSITIVE_INFINITY;
            double maxRisk = Double.NEGATIVE_INFINITY;
            double minReturn = Double.POSITIVE_INFINITY;
            double maxReturn = Double.NEGATIVE_INFINITY;
            for (PortfolioResult p : portfolios) {
                minRisk = Math.min(minRisk, p.annualVolatility);
                maxRisk = Math.max(maxRisk, p.annualVolatility);
                minReturn = Math.min(minReturn, p.annualReturn);
                maxReturn = Math.max(maxReturn, p.annualReturn);
            }
            minRisk *= 0.85;
            maxRisk *= 1.15;
            double padReturn = Math.max(0.02, (maxReturn - minReturn) * 0.25);
            minReturn -= padReturn;
            maxReturn += padReturn;
            drawPortfolioPoint(g2, area, equal, minRisk, maxRisk, minReturn, maxReturn, equalColor, "Equal");
            drawPortfolioPoint(g2, area, minCvar, minRisk, maxRisk, minReturn, maxReturn, warningColor, "Min CVaR");
            drawPortfolioPoint(g2, area, tda, minRisk, maxRisk, minReturn, maxReturn, tdaColor, "TDA");
            tickLabels(g2, area, minReturn, maxReturn);
            note(g2, "Καλύτερη περιοχή: αριστερά και πάνω", (int) area.getMinX() + 15, (int) area.getMinY() + 28);
            legend(g2,
            new String[] {"Equal Weight", "Min CVaR", "TDA Portfolio"},
            new Color[] {equalColor, warningColor, tdaColor},
            (int) area.getMaxX() - 150,
            (int) area.getMinY() + 30);
        }
        void drawPortfolioPoint(Graphics2D g2, Rectangle2D area, PortfolioResult p, double minRisk, double maxRisk, double minReturn, double maxReturn, Color color, String label) {
            int x = sx(area, p.annualVolatility, minRisk, maxRisk);
            int y = sy(area, p.annualReturn, minReturn, maxReturn);
            g2.setColor(color);
            g2.fill(new Ellipse2D.Double(x - 13, y - 13, 26, 26));
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Ellipse2D.Double(x - 13, y - 13, 26, 26));
            g2.setFont(labelFont);
            g2.drawString(label, x + 17, y - 8);
            g2.setFont(smallFont);
            g2.drawString("R=" + Format.pct(p.annualReturn), x + 17, y + 8);
            g2.drawString("V=" + Format.pct(p.annualVolatility), x + 17, y + 23);
        }
    }
    static final class PersistenceDiagramPanel extends ChartPanel {
        final PersistenceResult result;
        PersistenceDiagramPanel(PersistenceResult result) {
            this.result = result;
        }
        @Override
        void draw(Graphics2D g2) {
            title(g2, "4. Απλό persistence diagram", "Κρατούνται μόνο τα πιο έντονα σημεία ώστε να φαίνεται η τοπολογική πληροφορία καθαρά");
            Rectangle2D area = mainArea();
            grid(g2, area);
            axes(g2, area, "birth", "death");
            double max = result.infinityCap;
            g2.setColor(new Color(90, 90, 90));
            g2.setStroke(new BasicStroke(2.0f));
            g2.draw(new Line2D.Double(area.getMinX(), area.getMaxY(), area.getMaxX(), area.getMinY()));
            drawTopPoints(g2, area, result.h0, max, safeColor, 7, "H0");
            drawTopPoints(g2, area, result.h1, max, badColor, 7, "H1");
            legend(g2,
            new String[] {"H0: ομάδες", "H1: κύκλοι", "διαγώνιος"},
            new Color[] {safeColor, badColor, new Color(90, 90, 90)},
            (int) area.getMaxX() - 135,
            (int) area.getMinY() + 30);
            note(g2, "Όσο πιο μακριά από τη διαγώνιο, τόσο πιο σημαντικό είναι το χαρακτηριστικό", (int) area.getMinX() + 15, (int) area.getMaxY() + 68);
        }
        void drawTopPoints(Graphics2D g2, Rectangle2D area, List<PersistenceInterval> intervals, double max, Color color, int count, String label) {
            List<PersistenceInterval> top = topIntervals(intervals, count);
            g2.setColor(color);
            for (int i = 0; i < top.size(); i++) {
                PersistenceInterval in = top.get(i);
                double death = Math.min(in.death, max);
                int x = sx(area, in.birth, 0.0, max);
                int y = sy(area, death, 0.0, max);
                int radius = 9;
                g2.fill(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
                g2.setColor(Color.BLACK);
                g2.draw(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
                g2.setFont(smallFont);
                g2.drawString(label, x + 11, y + 4);
                g2.setColor(color);
            }
        }
        List<PersistenceInterval> topIntervals(List<PersistenceInterval> intervals, int count) {
            List<PersistenceInterval> copy = new ArrayList<>(intervals);
            copy.sort((a, b) -> Double.compare(b.persistence(), a.persistence()));
            if (copy.size() > count) {
                return new ArrayList<>(copy.subList(0, count));
            }
            return copy;
        }
    }
    static final class BarcodePanel extends ChartPanel {
        final PersistenceResult result;
        BarcodePanel(PersistenceResult result) {
            this.result = result;
        }
        @Override
        void draw(Graphics2D g2) {
            title(g2, "5. Απλό barcode", "Εμφανίζονται μόνο οι 10 μεγαλύτερες μπάρες για να μη μπερδεύεται το γράφημα");
            Rectangle2D area = mainArea();
            axes(g2, area, "filtration", "χαρακτηριστικά");
            List<PersistenceInterval> top = allTopIntervals(10);
            double max = result.infinityCap;
            double row = area.getHeight() / Math.max(1, top.size());
            for (int i = 0; i < top.size(); i++) {
                PersistenceInterval in = top.get(i);
                int y = (int) (area.getMinY() + i * row + row * 0.55);
                int x1 = sx(area, in.birth, 0.0, max);
                int x2 = sx(area, Math.min(in.death, max), 0.0, max);
                Color color = in.dimension == 0 ? safeColor : badColor;
                g2.setColor(color);
                g2.setStroke(new BasicStroke(9.0f));
                g2.draw(new Line2D.Double(x1, y, x2, y));
                g2.setColor(Color.BLACK);
                g2.setFont(labelFont);
                String text = (in.dimension == 0 ? "Ομάδα" : "Κύκλος") + " " + (i + 1);
                g2.drawString(text, (int) area.getMinX() - 68, y + 5);
                g2.setFont(smallFont);
                g2.drawString("διάρκεια " + Format.num(in.persistence()), x2 + 8, y + 4);
            }
            legend(g2,
            new String[] {"H0: ομάδα τίτλων", "H1: κύκλος κινδύνου"},
            new Color[] {safeColor, badColor},
            (int) area.getMaxX() - 160,
            (int) area.getMinY() + 25);
            note(g2, "Μεγαλύτερη μπάρα = πιο σταθερό τοπολογικό χαρακτηριστικό", (int) area.getMinX() + 15, (int) area.getMaxY() + 68);
        }
        List<PersistenceInterval> allTopIntervals(int count) {
            List<PersistenceInterval> all = new ArrayList<>();
            all.addAll(result.h0);
            all.addAll(result.h1);
            all.sort((a, b) -> Double.compare(b.persistence(), a.persistence()));
            if (all.size() > count) {
                return new ArrayList<>(all.subList(0, count));
            }
            return all;
        }
    }
    static final class PortfolioComparisonPanel extends ChartPanel {
        final MarketData data;
        final PortfolioResult equal;
        final PortfolioResult minCvar;
        final PortfolioResult tda;
        PortfolioComparisonPanel(MarketData data, PortfolioResult equal, PortfolioResult minCvar, PortfolioResult tda) {
            this.data = data;
            this.equal = equal;
            this.minCvar = minCvar;
            this.tda = tda;
        }
        @Override
        void draw(Graphics2D g2) {
            title(g2, "6. Απλή σύγκριση κινδύνου", "Μόνο μπάρες: όσο πιο χαμηλή είναι η μπάρα, τόσο μικρότερος είναι ο κίνδυνος");
            Rectangle2D area = mainArea();
            String[] metrics = {"VaR", "CVaR", "MaxDD", "Volatility"};
            PortfolioResult[] portfolios = {equal, minCvar, tda};
            String[] names = {"Equal", "Min CVaR", "TDA"};
            Color[] colors = {equalColor, warningColor, tdaColor};
            double[][] values = valuesForMetrics(portfolios);
            double max = maxValue(values);
            axes(g2, area, "μετρικές κινδύνου", "ποσοστό");
            grid(g2, area);
            double groupWidth = area.getWidth() / metrics.length;
            double barWidth = Math.min(42, groupWidth / 5.0);
            for (int m = 0; m < metrics.length; m++) {
                double baseX = area.getMinX() + m * groupWidth + groupWidth * 0.22;
                for (int p = 0; p < portfolios.length; p++) {
                    double h = area.getHeight() * values[p][m] / Math.max(1.0e-9, max);
                    double x = baseX + p * (barWidth + 8);
                    double y = area.getMaxY() - h;
                    g2.setColor(colors[p]);
                    g2.fill(new Rectangle2D.Double(x, y, barWidth, h));
                    g2.setColor(Color.BLACK);
                    g2.draw(new Rectangle2D.Double(x, y, barWidth, h));
                    g2.setFont(smallFont);
                    drawCentered(g2, Format.pct(values[p][m]), (int) (x + barWidth / 2.0), (int) y - 5);
                }
                g2.setFont(labelFont);
                g2.setColor(Color.BLACK);
                drawCentered(g2, metrics[m], (int) (area.getMinX() + m * groupWidth + groupWidth / 2.0), (int) area.getMaxY() + 28);
            }
            tickLabels(g2, area, 0.0, max);
            legend(g2, names, colors, (int) area.getMaxX() - 135, (int) area.getMinY() + 30);
            note(g2, "Το ζητούμενο είναι χαμηλότερο VaR, CVaR, MaxDD και volatility", (int) area.getMinX() + 15, (int) area.getMaxY() + 68);
        }
        double[][] valuesForMetrics(PortfolioResult[] portfolios) {
            double[][] values = new double[portfolios.length][4];
            for (int i = 0; i < portfolios.length; i++) {
                values[i][0] = portfolios[i].valueAtRisk;
                values[i][1] = portfolios[i].conditionalValueAtRisk;
                values[i][2] = portfolios[i].maxDrawdown;
                values[i][3] = portfolios[i].annualVolatility;
            }
            return values;
        }
        double maxValue(double[][] values) {
            double max = 0.0;
            for (double[] row : values) {
                for (double v : row) {
                    max = Math.max(max, v);
                }
            }
            return max * 1.18;
        }
    }
    static final class Format {
        static final DecimalFormat PCT = new DecimalFormat("0.00%");
        static final DecimalFormat NUM = new DecimalFormat("0.0000");
        static String pct(double x) {
            return PCT.format(x);
        }
        static String num(double x) {
            return NUM.format(x);
        }
    }
}
