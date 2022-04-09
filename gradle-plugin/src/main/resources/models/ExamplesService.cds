// Please note that this service has no cds namespace declaration

service ExamplesService {
   entity Example {
      key Id : Integer;
      Title : String;
      Author : String;
   }
}