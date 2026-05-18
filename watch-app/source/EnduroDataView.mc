import Toybox.Activity;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

const METERS_PER_MILE = 1609.344;

class EnduroDataField extends WatchUi.DataField {

    private var _pace         as String = "--:--";
    private var _dist         as String = "-.--";
    private var _elapsed      as String = "-:--:--";
    private var _hr           as String = "--";
    private var _lastDist     as Float  = 0.0;
    private var _lastTime     as Number = 0;
    private var _lapStartDist as Float  = 0.0;
    private var _lapStartTime as Number = 0;
    private var _logo as BitmapResource? = null;

    public function initialize() {
        DataField.initialize();
    }

    public function onLayout(dc as Dc) as Void {
        _logo = WatchUi.loadResource(Rez.Drawables.EverysightLogo) as BitmapResource;
    }

    public function compute(info as Activity.Info) as Void {
        var speed = info.currentSpeed;
        if (speed != null && speed > 0.2) {
            var totalSec = (METERS_PER_MILE / speed).toNumber();
            _pace = (totalSec / 60).format("%d") + ":" + (totalSec % 60).format("%02d");
        } else {
            _pace = "--:--";
        }

        var d = info.elapsedDistance;
        if (d != null) { _lastDist = d; }
        _dist = (d != null) ? (d / METERS_PER_MILE).format("%.2f") : "-.--";

        var t = info.timerTime;
        if (t != null) { _lastTime = t; }
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

        Communications.transmit({
            "pace" => _pace,
            "dist" => _dist,
            "time" => _elapsed,
            "hr"   => _hr
        }, null, new $.SendListener());
    }

    public function onTimerLap() as Void {
        var lapDist = _lastDist - _lapStartDist;
        var lapTime = _lastTime - _lapStartTime;
        _lapStartDist = _lastDist;
        _lapStartTime = _lastTime;

        if (lapDist > 0 && lapTime > 0) {
            var lapPaceSec = ((lapTime / 1000.0) / (lapDist / METERS_PER_MILE)).toNumber();
            var lapPace = (lapPaceSec / 60).format("%d") + ":" + (lapPaceSec % 60).format("%02d");
            Communications.transmit({"lap_pace" => lapPace}, null, new $.SendListener());
        }
    }

    public function onUpdate(dc as Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();

        dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
        dc.clear();

        if (_logo != null) {
            var logo = _logo as BitmapResource;
            dc.drawBitmap((w - logo.getWidth()) / 2, (h - logo.getHeight()) / 2, logo);
        }
    }
}
