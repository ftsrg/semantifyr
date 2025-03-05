package Interfaces {

	item def Toggle;
	item def PoliceInterrupt;
	
	enum def Light {
		None; 
		enum Red; 
		enum Yellow; 
		enum Green;
	}
	
	item def Display {
		attribute light : Light;
	}
	
	port def ControlPort {
		in item toggle : Toggle;
	}
	
	port def PolicePort {
		in item interrupt : PoliceInterrupt;
	}
	
	port def DisplayPort {
		out item display : Display;
	}
	
	abstract part def ControllableComponent {
		port control : ControlPort;
		port police : PolicePort;
	}
}
