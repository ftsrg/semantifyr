package TrafficLight {
	
	import Requirements::*;
	import ScalarValues::*;
	import Interfaces::*;
	import SI::s;
	
	part def TrafficLight :> ControllableComponent {
	
		port display : DisplayPort;
		
		attribute greenOn : Boolean := false;
		attribute yellowOn : Boolean := false;
		attribute redOn : Boolean := false;

		exhibit state TrafficLightStates {
			entry; then Normal;
			
			state Normal {
			
				entry; then Red;
			
				state Red {
					entry action {
						assign redOn := true;
						send Display(light = Light::Red) to display;
					}
					
					exit action {
						assign redOn := false;
					}
				}
				
				accept : Toggle via control then Green;
					
				state Green {
					entry action {
						assign greenOn := true;
						send Display(light = Light::Green) to display;
					}
					exit action {
						assign greenOn := false;
					}
				}
				
				accept toggle : Toggle via control then Yellow;
					
				state Yellow {
					entry action {
						assign yellowOn := true;
						send Display(light = Light::Yellow) to display;
					}
					exit action {
						assign yellowOn := false;
					}
				}
				
				accept toggle : Toggle via control then Red;
		
			}
				
			accept interrupt : PoliceInterrupt via police then Interrupted;

			state Interrupted {
				entry; then BlinkingYellow;
				
				state BlinkingYellow {
					entry action {
						assign yellowOn := true;
						send Display(light = Light::Yellow) to display;
					}
					exit action {
						assign yellowOn := false;
					}
				}
				
				accept toggle : Toggle via control then Blank;
					
				state Blank {
					entry action {
						send Display(light = Light::None) to display;
					}	
				}
					
				accept toggle : Toggle via control then BlinkingYellow;
			}
			
			accept interrupt : PoliceInterrupt via police then Normal;
		}

	}

}
