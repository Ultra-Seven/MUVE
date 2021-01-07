import _ from "underscore";

class Dob_Job {
    constructor() {
        this.columns = [
            "Job", "Document", "Borough", "House", "Street", "Block", "Lot",
            "Job Type", "Job Status Description", "Action Year", "Action Month",
            "Action Day", "Action Time", "Building", "Applicant First Name",
            "Applicant Last Name", "Applicant Title", "Filing Year", "Filing Month",
            "Filing Day", "Paid Year", "Paid Month", "Paid Day", "Fully Paid Year",
            "Fully Paid Month", "Fully Paid Day", "Assigned Year", "Assigned Month",
            "Assigned Day", "Approved Year", "Approved Month", "Approved Day",
            "Initial Cost", "Estimate Fee", "Fee Status", "Existing Zoning",
            "Proposed Zoning", "Enlargement Footage", "Street Frontage", "Existingno",
            "Proposedvno", "Existing Height", "Proposed Height", "Existing Dwelling",
            "Proposed Dwelling", "Owner Type", "Owner First Name", "Owner Last Name",
            "Business Name", "Owner House Street", "City", "State", "Zip", "Job Description",
            "Dobrundate Year", "Dobrundate month", "Dobrundate day", "Total Construction"
        ];
        this.samples = [
            "How many cases in Brooklyn?",
            "How many cases for noise?"
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

export default Dob_Job;