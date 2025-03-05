package Crossroads {
	
	import Interfaces::*;
	import Requirements::*;
	import TrafficLight::TrafficLight;
	import Controller::Controller;
	
	part def Crossroads :> ControllableComponent {		
		port displayA : DisplayPort;
		port displayB : DisplayPort;
		
		part controller : Controller;
		part trafficLightA : TrafficLight;
		part trafficLightB : TrafficLight;
		
		bind controller.control = control;
		bind controller.police = police;
		bind trafficLightA.display = displayA;
		bind trafficLightB.display = displayB;
		
		connect controller.controlA to trafficLightA.control;
		connect controller.policeA to trafficLightA.police;
		
		connect controller.controlB to trafficLightB.control;
		connect controller.policeB to trafficLightB.police;
	}

}
