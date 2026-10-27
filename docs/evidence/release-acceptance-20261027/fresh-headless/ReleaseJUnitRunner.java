import java.io.PrintWriter;
import java.util.List;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public final class ReleaseJUnitRunner {
    private static final List<String> TEST_CLASSES = List.of(
        "seoultech.se.tetris.blocks.ItemDistributionTest",
        "seoultech.se.tetris.component.AnchorAcceptanceTest",
        "seoultech.se.tetris.component.LineItemAcceptanceTest",
        "seoultech.se.tetris.component.ColumnBombAcceptanceTest",
        "seoultech.se.tetris.component.CrossItemAcceptanceTest",
        "seoultech.se.tetris.component.ItemSupplyAcceptanceTest",
        "seoultech.se.tetris.component.ModeScoreUiTest",
        "seoultech.se.tetris.score.ScoreStoreModeTest"
    );

    private ReleaseJUnitRunner() {}

    public static void main(String[] args) {
        List<DiscoverySelector> selectors = TEST_CLASSES.stream()
            .map(DiscoverySelectors::selectClass)
            .map(DiscoverySelector.class::cast)
            .toList();
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectors)
            .build();
        SummaryGeneratingListener summary = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(summary);
        launcher.execute(request);
        summary.getSummary().printTo(new PrintWriter(System.out, true));
        summary.getSummary().printFailuresTo(new PrintWriter(System.err, true));
        if (summary.getSummary().getTestsFailedCount() != 0
                || summary.getSummary().getTestsAbortedCount() != 0
                || summary.getSummary().getTestsSkippedCount() != 0) {
            System.exit(1);
        }
    }
}
