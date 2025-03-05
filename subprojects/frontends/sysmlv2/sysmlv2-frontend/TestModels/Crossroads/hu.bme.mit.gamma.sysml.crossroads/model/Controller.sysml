package Controller {
	
	import ScalarValues::Integer;
	import Interfaces::*;
	
	part def CountingComponent {
		attribute count : Integer := 0;
	}
			
	part def Controller :> CountingComponent, ControllableComponent {
		
		port controlA : ~ControlPort;
		port policeA : ~PolicePort;
		
		port controlB : ~ControlPort;
		port policeB : ~PolicePort;
		
		attribute trafficDuration : Integer := 5;
		attribute stoppingDuration : Integer := 4;

		exhibit state TrafficLightStates {
			entry; then Init;
			
			state Init;
			
			accept toggle : Toggle via control do action {
				// Switch A to Green
				send Toggle() to controlA;
				assign count := 0;
			} then Normal;
			
			state Normal {
				entry; then TrafficOnA;
				
				state TrafficOnA;

				accept toggle : Toggle via control if (count + 1 >= trafficDuration) do action {
					assign count := 0;
					// Switch A to Yellow
					send Toggle() to controlA;
				} then StoppingA;
				
				accept toggle : Toggle via control if (count + 1 < trafficDuration) do action {
					assign count := count + 1;
				} then TrafficOnA;
				
				state StoppingA;
			
				accept toggle : Toggle via control if (count + 1 >= stoppingDuration) do action {
					assign count := 0;
					// Switch A to Red, B to Green
					send Toggle() to controlA;
					send Toggle() to controlB;
				} then TrafficOnB;
				
				accept toggle : Toggle via control if (count + 1 < stoppingDuration) do action {
					assign count := count + 1;
				} then StoppingA;
					
				state TrafficOnB;
				
				accept toggle : Toggle via control if (count + 1 >= trafficDuration) do action {
					assign count := 0;
					// Switch B to Yellow
					send Toggle() to controlB;
				} then StoppingB;
				
				accept toggle : Toggle via control if (count + 1 < trafficDuration) do action {
					assign count := count + 1;
				} then TrafficOnB;
					
				state StoppingB;
				
				accept toggle : Toggle via control if (count + 1 >= stoppingDuration) do action {
					assign count := 0;
					// Switch B to Red, A to Green
					send Toggle() to controlA;
					send Toggle() to controlB;
				} then TrafficOnA;
				
				accept toggle : Toggle via control if (count + 1 < stoppingDuration) do action {
					assign count := count + 1;
				} then StoppingB;				
			}
			
			accept interrupt : PoliceInterrupt via police do action {
				send PoliceInterrupt() to policeA;
				send PoliceInterrupt() to policeB;
			} then Interrupted;
				
			state Interrupted;
			
			accept interrupt : PoliceInterrupt via police do action {
				send PoliceInterrupt() to policeA;
				// send PoliceInterrupt() to policeB; // Be careful! Without this line the lights get out of sync upon a PoliceInterrupt!
            } then Init; // This should be replaced with history state
				
			transition first Interrupted accept toggle : Toggle via control do action {
				send Toggle() to controlA;
				send Toggle() to controlB;
			} then Interrupted;
		}
	}

}
