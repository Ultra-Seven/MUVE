import _ from "underscore";

class Sample_311 {
    constructor() {
        this.columns = [
            "Unique Key", "Created Date", "Closed Date", "Agency", "Agency Name",
            "Complaint Type", "Descriptor", "Location Type", "Incident Zip",
            "Incident Address", "Street Name", "Cross Street 1", "Cross Street 2",
            "Intersection Street 1", "Intersection Street 2", "Address Type", "City",
            "Landmark", "Facility Type", "Status", "Due Date", "Resolution Description",
            "Resolution Action Updated Date", "Community Board", "BBL", "Borough",
            "X Coordinate", "Y Coordinate", "Open Data Channel Type", "Park Facility Name",
            "Park Borough", "Vehicle Type", "Taxi Company Borough", "Taxi Pick Up Location",
            "Bridge Highway Name", "Bridge Highway Direction", "Road Ramp", "Bridge Highway Segment",
            "Latitude", "Longitude", "Location"
        ];
        this.samples = [
            "How many cases in Brooklyn?"
        ];
    }

    setup() {
        $("#columns").empty();
        $("#queries").empty();
        _.each(this.columns, column => {
            $("#columns").append("<li>" + column + "</li>")
        })

        _.each(this.samples, sample => {
            $("#queries").append("<li>" + sample + "</li>")
        })
    }
}
export default Sample_311;