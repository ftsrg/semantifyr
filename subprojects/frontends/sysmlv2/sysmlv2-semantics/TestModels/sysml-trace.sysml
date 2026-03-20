package Model {
    part def MySystem {
        attribute x : ScalarValues::Integer default 0;
        action myBehavior {
            first startShot;
            succession __unnamed1 first startShot then m;
            merge m;
            succession __unnamed2 first m then myStep;
            action myStep assign x := x + 1;
            succession __unnamed3 first myStep then d;
            decide d;
            succession repeat first d if x < 2 then m;
            succession finish first d if x >= 2 then done;
        }
    }
    part def ExecutedSystem :> MySystem {
        private import Occurrences::HappensJustBefore;
        private import Occurrences::HappensWhile;
        :>> timeSlices : MySystem;
        :>> snapshots : MySystem;
        action :>> myBehavior {
            first startShot;
            succession :> __unnamed1 first startShot then m1;
            merge m1 :> m;
            succession :> __unnamed2 first m1 then myStep1;
            succession :> __unnamed3 first myStep1 then d1;
            decide d1 :> d;
            succession repeat1 :> repeat first d1 if x < 2 then m2;
            merge m2 :> m;
            succession :> __unnamed2 first m2 then myStep2;
            succession :> __unnamed3 first myStep2 then d2;
            decide d2 :> d;
            succession finish1 :> finish first d2 if x >= 2 then done;
            action myStep1 :> myStep;
            action myStep2 :> myStep;
        }
        timeslice before_myBehavior {
            attribute :>> x = 0;
        }
        succession : HappensJustBefore first before_myBehavior then myBehavior_starts;
        snapshot myBehavior_starts {
            attribute :>> x = 0;
        }
        succession : HappensWhile first myBehavior.startShot then myBehavior_starts;
        succession : HappensJustBefore first myBehavior_starts then between_start_and_m1;
        timeslice between_start_and_m1 {
            attribute :>> x = 0;
        }
        succession : HappensJustBefore first between_start_and_m1 then m1_performing;
        timeslice m1_performing {
            attribute :>> x = 0;
        }
        succession : HappensWhile first myBehavior.m1 then m1_performing;
        succession : HappensJustBefore first m1_performing then between_m1_and_myStep1;
        timeslice between_m1_and_myStep1 {
            attribute :>> x = 0;
        }
        succession : HappensJustBefore first between_m1_and_myStep1 then myStep1_performing;
        timeslice myStep1_performing {
            attribute :>> x = 0;
        }
        succession : HappensWhile first myBehavior.myStep1 then myStep1_performing;
        succession : HappensJustBefore first myStep1_performing then between_myStep1_and_d1;
        timeslice between_myStep1_and_d1 {
            attribute :>> x = 1;
        }
        succession : HappensJustBefore first between_myStep1_and_d1 then d1_performing;
        timeslice d1_performing {
            attribute :>> x = 1;
        }
        succession : HappensWhile first myBehavior.d1 then d1_performing;
        succession : HappensJustBefore first d1_performing then between_d1_and_m2;
        timeslice between_d1_and_m2 {
            attribute :>> x = 1;
        }
        succession : HappensJustBefore first between_d1_and_m2 then m2_performing;
        timeslice m2_performing {
            attribute :>> x = 1;
        }
        succession : HappensWhile first myBehavior.m2 then m2_performing;
        succession : HappensJustBefore first m2_performing then between_m2_and_myStep2;
        timeslice between_m2_and_myStep2 {
            attribute :>> x = 1;
        }
        succession : HappensJustBefore first between_m2_and_myStep2 then myStep2_performing;
        timeslice myStep2_performing {
            attribute :>> x = 1;
        }
        succession : HappensWhile first myBehavior.myStep2 then myStep2_performing;
        succession : HappensJustBefore first myStep2_performing then between_myStep2_and_d2;
        timeslice between_myStep2_and_d2 {
            attribute :>> x = 2;
        }
        succession : HappensJustBefore first between_myStep2_and_d2 then d2_performing;
        timeslice d2_performing {
            attribute :>> x = 2;
        }
        succession : HappensWhile first myBehavior.d2 then d2_performing;
        succession : HappensJustBefore first d2_performing then between_d2_and_done;
        timeslice between_d2_and_done {
            attribute :>> x = 2;
        }
        succession : HappensJustBefore first between_d2_and_done then myBehavior_done;
        snapshot myBehavior_done {
            attribute :>> x = 2;
        }
        succession : HappensWhile first myBehavior.done then myBehavior_done;
        succession : HappensJustBefore first myBehavior_done then after_myBehavior;
        timeslice after_myBehavior {
            attribute :>> x = 2;
        }
    }
}
