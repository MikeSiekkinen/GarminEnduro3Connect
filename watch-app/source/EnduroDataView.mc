import Toybox.Activity;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

class EnduroDataField extends WatchUi.DataField {

    private var _pace    as String = "--:--";
    private var _dist    as String = "-.--";
    private var _elapsed as String = "-:--:--";
    private var _hr      as String = "--";
    private var _tick    as Number = 0;

    public function initialize() {
        DataField.initialize();
    }

    public function onLayout(dc as Dc) as Void {
    }

    public function compute(info as Activity.Info) as Void {
        var speed = info.currentSpeed;
        if (speed != null && speed > 0.2) {
            var totalSec = (1000.0 / speed).toNumber();
            _pace = (totalSec / 60).format("%d") + ":" + (totalSec % 60).format("%02d");
        } else {
            _pace = "--:--";
        }

        var d = info.elapsedDistance;
        _dist = (d != null) ? (d / 1000.0).format("%.2f") : "-.--";

        var t = info.timerTime;
        if (t != null) {
            var s = t / 1000;
            _elapsed = (s / 3600).format("%d") + ":" +
                       ((s % 3600) / 60).format("%02d") + ":" +
                       (s % 60).format("%02d");
        } else {
            _elapsed = "-:--:--";
        }

        var h = info.currentHeartRate;
        _hr = (h != null) ? h.format("%d") : "--";

        _tick++;
        if (_tick % 5 == 0) {
            Communications.transmit({
                "pace" => _pace,
                "dist" => _dist,
                "time" => _elapsed,
                "hr"   => _hr
            }, null, new $.SendListener());
        }
    }

    public function onUpdate(dc as Dc) as Void {
        var w  = dc.getWidth();
        var h  = dc.getHeight();
        var cx = w / 2;

        dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
        dc.clear();

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.05, Graphics.FONT_TINY, "PACE", Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.18, Graphics.FONT_LARGE, _pace, Graphics.TEXT_JUSTIFY_CENTER);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.42, Graphics.FONT_TINY, "KM", Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.52, Graphics.FONT_MEDIUM, _dist, Graphics.TEXT_JUSTIFY_CENTER);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.68, Graphics.FONT_TINY, "TIME", Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.76, Graphics.FONT_SMALL, _elapsed, Graphics.TEXT_JUSTIFY_CENTER);

        dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 0.88, Graphics.FONT_TINY, _hr + " bpm", Graphics.TEXT_JUSTIFY_CENTER);
    }
}
