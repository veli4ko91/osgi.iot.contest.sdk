package osgi.enroute.trains.hw.provider;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import osgi.enroute.debug.api.Debug;
import osgi.enroute.iot.lego.adapter.Channel;
import osgi.enroute.iot.lego.adapter.LegoRC;
import osgi.enroute.iot.pi.lirc.provider.LIRCImpl;
import osgi.enroute.trains.hw.provider.TrainControllerImpl.Config;
import osgi.enroute.trains.train.api.TrainController;

/**
 * 
 */
@Designate(ocd = Config.class, factory=true)
@Component(name = "osgi.enroute.trains.hw.train", immediate = true, property = { Debug.COMMAND_SCOPE + "=tc",
		Debug.COMMAND_FUNCTION + "=tc", Debug.COMMAND_FUNCTION + "=move", Debug.COMMAND_FUNCTION + "=light",
		"service.exported.interfaces=*" }, configurationPolicy=ConfigurationPolicy.REQUIRE)
public class TrainControllerImpl extends LegoRC implements TrainController {

	@ObjectClassDefinition
	@interface Config {
		Channel channel() default Channel.CH1;

		String train_rfid();

		String train_name();

		int divider() default 400;
	}

	private double divider;
	private Config config;

	@Activate
	void start(Config config) throws Exception {
		this.config = config;
		this.setWave(new LIRCImpl());
		super.activate(config.channel());
		this.divider = config.divider();
	}

	@Override
	public String toString() {
		return "TrainController[channel=" + config.channel() + ", rfid=" + config.train_rfid() + ", name="
				+ config.train_name() + ", divider=" + config.divider() + "]";
	}

	@Override
	public void move(int directionAndSpeed) {
		try {
			Double speed = directionAndSpeed / divider;
			A(speed);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void light(boolean on) {
		try {
			B(on ? 1D : 0D);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}