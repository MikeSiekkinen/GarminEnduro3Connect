import Toybox.Communications;
import Toybox.System;

class SendListener extends Communications.ConnectionListener {
    public function initialize() {
        Communications.ConnectionListener.initialize();
    }
    public function onComplete() as Void {}
    public function onError() as Void {
        System.println("TX error");
    }
}
