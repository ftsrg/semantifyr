private import Occurrences::HappensJustBefore;
private import Occurrences::HappensWhile;

part def TrafficLightControllerTrace :> TrafficLightController {
 :>> timeSlices : TrafficLightController;
 state B :>> Behaviour {
  state n1 :> Normal {
   state y1 :> Yellow; action tyr1 :> tyr; state r1 :> Red; }
  action tni1 :> tni;
  state i1 :> Interrupted { state y1 :> Yellow; }
  action tin1 :> tin;
  state n2 :> Normal { state y2 :> Yellow; } }
 timeslice before; // before beginning execution
 succession : HappensJustBefore first before then start;
 timeslice start; // during startShot
 succession : HappensWhile first B.startShot then start;
 succession : HappensJustBefore first start then NY;
 timeslice NY; // at state Normal.Yellow
 succession : HappensWhile first B.n1 then NY;
 succession : HappensWhile first B.n1.y1 then NY;
 succession : HappensJustBefore first NY then NYtoNR;
 timeslice NYtoNR; // executing transition Yellow -> Red
 succession : HappensWhile first B.n1 then NYtoNR;
 succession : HappensWhile first B.n1.tyr1 then NYtoNR;
 succession : HappensJustBefore first NYtoNR then NR;
 timeslice NR; // at state Normal.Red
 succession : HappensWhile first B.n1 then NYtoNR;
 succession : HappensWhile first B.n1.tyr1 then NYtoNR;
 succession : HappensJustBefore first NYtoNR then NR;
 timeslice NtoI; // executing transition Normal -> Interrupted
 succession : HappensWhile first B.tni1 then NtoI;
 succession : HappensJustBefore first NtoI then IY;
 timeslice IY; // at state Interrupted.Yellow
 succession : HappensWhile first B.i1 then IY;
 succession : HappensWhile first B.i1.y1 then IY;
 succession : HappensJustBefore first IY then NR;
 timeslice ItoN2; // executing transition Interrupted -> Normal
 succession : HappensWhile first B.tin1 then ItoN2;
 succession : HappensJustBefore first ItoN2 then IY;
 timeslice NY2; // at state Normal.Yellow
 succession : HappensWhile first B.n2 then NY2;
 succession : HappensWhile first B.n2.y2 then NY2; }
