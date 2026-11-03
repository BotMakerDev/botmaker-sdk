package com.botmaker.sdk.plugin.run;

import com.botmaker.plugin.api.ReportViewHolder;
import com.botmaker.plugin.api.run.ReportView;
import com.botmaker.sdk.internal.screens.ScreenValues;

import java.util.List;

/**
 * The SDK's Screens report, as views of the Reports tab: what the values its screen lines carry ({@link ScreenValues})
 * count. Problems per screen, failed moves (from × to), the screens a walk was going to when the game showed none of
 * the map's, and, from a local run's last lines, the time spent on each screen.
 */
public final class SdkReportViews implements ReportViewHolder {

    @Override
    public List<ReportView> reportViews() {
        return List.of(
                ReportView.of("Problems per screen", ReportView.Chart.BARS, ScreenValues.SCREEN),
                ReportView.of("Failed moves", ReportView.Chart.HEATMAP, ScreenValues.FROM).against(ScreenValues.TO),
                ReportView.of("Unrecognised screens", ReportView.Chart.TABLE, ScreenValues.TARGET),
                ReportView.of("Time on screens", ReportView.Chart.DONUT, ScreenValues.SCREEN)
                        .counting(ReportView.Lines.EVERY_LINE).weighedBy(ScreenValues.MILLIS));
    }
}
