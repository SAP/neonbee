// You can find the reference of all Core Data Service (CDS) concepts as well as the CDS Definition Language (CDL)
// features here: https://cap.cloud.sap/docs/cds/cdl

namespace io.neonbee.test1;

// CDS allows to define service interfaces as collections of exposed entities enclosed in a service block
service TestService1 {

    entity AllPropertiesNullable {
        key KeyPropertyString: String;
        PropertyString : String;
        PropertyString100 : String(100);
        PropertyLargeString : LargeString;
        PropertyBinary : Binary;
        PropertyBinary100 : Binary(100);
        PropertyLargeBinary : LargeBinary;
        PropertyBoolean : Boolean;
        PropertyDate : Date;
        PropertyTime : Time;
        PropertyDateTime : DateTime; // sec precision
        PropertyTimestamp : Timestamp; // µs precision
        PropertyDecimal : Decimal(11,5);
        PropertyDecimalFloat : DecimalFloat;
        PropertyDouble : Double;
        PropertyUuid : UUID;
        PropertyInt32 : Integer;
        PropertyInt64 : Integer64;
    }

    entity TestProducts {
        key ID : String;
        name : String not null;
        description : String;
    }
}