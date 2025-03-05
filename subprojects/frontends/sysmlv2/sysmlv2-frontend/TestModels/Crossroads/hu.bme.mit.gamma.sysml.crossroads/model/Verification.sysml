package Verification {
	 
	import VerificationCases::VerdictKind;
	import GammaLib::*;
	
	import Requirements::*;
	import Interfaces::*;
	import Crossroads::Crossroads;

	verification def 'Crossroad both green' {
		doc /*
		 * Both roads should never be green at the same time.
		 */

		subject crossroads : Crossroads {
			:>> controller.trafficDuration := 5;
			:>> controller.stoppingDuration := 4;
		}

		objective {
			verify requirement {
				require constraint {
					AG(not (crossroads.trafficLightA.greenOn and crossroads.trafficLightB.greenOn))
				}
			}
		}

		return verdict : VerdictKind;
	}

	verification def 'Crossroad at some point green' {
		doc /*
		 * At some point the crossroad must turn green.
		 */

		subject crossroads : Crossroads {
			:>> controller.trafficDuration := 5;
			:>> controller.stoppingDuration := 4;
		}

		objective {
			verify requirement {
				require constraint {
					EF(crossroads.trafficLightA.greenOn or crossroads.trafficLightB.greenOn)
				}
			}
		}
		
		return verdict : VerdictKind;
	}
	
    verification def 'Crossroad one green, other yellow' {
		doc /*
		 * One road should never be yellow and the other green at the same time.
		 */

		subject crossroads : Crossroads {
			:>> controller.trafficDuration := 5;
			:>> controller.stoppingDuration := 2;
		}

		objective {
			verify requirement {
				require constraint {
					AG(not (crossroads.trafficLightA.yellowOn and crossroads.trafficLightB.greenOn))
				}
			}
		}
		
		return verdict : VerdictKind;
	}

    verification def 'Crossroad one yellow, other green' {
		doc /*
		 * One road should never be yellow and the other green at the same time. Same as above, but reversed.
		 */

		subject crossroads : Crossroads {
			:>> controller.trafficDuration := 5;
			:>> controller.stoppingDuration := 2;
		}

		objective {
			verify requirement {
				require constraint {
					AG(not (crossroads.trafficLightA.greenOn and crossroads.trafficLightB.yellowOn))
				}
			}
		}

		return verdict : VerdictKind;
	}
}
