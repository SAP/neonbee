// You can find the reference of all Core Data Service (CDS) concepts as well as the CDS Definition Language (CDL)
// features here: https://cap.cloud.sap/docs/cds/cdl

namespace io.neonbee.handler;

// CDS allows to define service interfaces as collections of exposed entities enclosed in a service block
service TestService {

    entity AllPropertiesNullable {
        key KeyPropertyString: String;
        PropertyString : String;
        PropertyChar : String;
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

    entity AllPropertiesNotNullable {
        key KeyPropertyString: String not null;
        PropertyString : String not null;
        PropertyChar : String not null;
        PropertyString100 : String(100) not null;
        PropertyLargeString : LargeString;
        PropertyBinary : Binary not null;
        PropertyBinary100 : Binary(100) not null;
        PropertyLargeBinary : LargeBinary;
        PropertyBoolean : Boolean not null;
        PropertyDate : Date not null;
        PropertyTime : Time not null;
        PropertyDateTime : DateTime; // sec precision
        PropertyTimestamp : Timestamp not null;
        PropertyDecimal : Decimal(11,5) not null;
        PropertyDecimalFloat : DecimalFloat;
        PropertyDouble : Double not null;
        PropertyUuid : UUID not null;
        PropertyInt32 : Integer not null;
        PropertyInt64 : Integer64 not null;
    }

    entity MultipleKeyProperties {
        key KeyPropertyString : String not null;
        key KeyPropertyInt32 : Integer not null;
        key KeyPropertyBoolean : Boolean;
    }

    // The following test entities (TestProducts and TestCategories) shows an bi-directional one-to-many association where 1 TestCategories entity has
    // a relationship to many TestProducts entities and every TestProducts entity has a backlink to it's related
    // TestCategories entity.
    // This association enables us navigation between the entities like '/odata/io.neonbee.test.TestService/TestProducts('testProductId')/testCategory'
    // or '/odata/io.neonbee.test.TestService/TestCategories('testCategoryId')/testProducts'.

    entity TestProducts {
        key ID : String;
        name : String not null;
        description : String;
        // The backlink can be any managed to-one association on the many side pointing back to the one side.
        testCategory: Association to TestCategories;
    }

    entity TestCategories {
        key ID : String;
        name : String not null;
        description : String;
        // Managed one-to-many association:
        // For one-to-many associations specify an on condition following the canonical expression pattern '<association>.<backlink> = $self'.
        testProducts : Association to many TestProducts on testProducts.testCategory = $self;
    }

}