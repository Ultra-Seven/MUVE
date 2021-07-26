import Baseline from "./engine/baseline";

const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const recognition = new SpeechRecognition();
import Sample_311 from "./dataset/sample_311";
import Sample_AU from "./dataset/sample_au";
import Config from "./config";
import Engine from "./engine/engine";
import Study from "./exp/user_study";
import Dob_Job from "./dataset/dob_job";
import Cognition from "./exp/cognition";
import AmbiguousStudy from "./exp/user_study_ambiguous";
import Customer from "./dataset/customer";
let name = "sample_311";
recognition.lang = 'en-US';
$("#btn-start-recording").click(() => {
    recognition.start();
    console.log('Ready to receive voice input.');
});

$("#datasets").on('change', function() {
    name = this.value;
    if (name === "sample_311") {
        sample311.setup();
    }
    else if (name === "sample_au") {
        sampleAU.setup();
    }
    else if (name === "dob_job") {
        dobJob.setup();
    }
    else if (name === "customer") {
        customer.setup()
    }
    else {
        console.log("NO datasets");
    }
});
let window_height = window.innerHeight
    || document.documentElement.clientHeight
    || document.body.clientHeight;
$("#list_content").height(Math.floor(window_height * 0.6) + "px");

$("#query_content").height(Math.floor(window_height * 0.12) + "px");

const sample311 = new Sample_311();
const sampleAU = new Sample_AU();
const dobJob = new Dob_Job();
const customer = new Customer();

customer.setup();

window.onresize = () => {
    window_height = window.innerHeight
        || document.documentElement.clientHeight
        || document.body.clientHeight;
    $("#list_content").height(Math.floor(window_height * 0.6) + "px");

    $("#query_content").height(Math.floor(window_height * 0.12) + "px");
}

const config = new Config();
const title = $(document).find("title").text();
let engine;
const queryString = window.location.search;
const urlParams = new URLSearchParams(queryString);
if (title === "MUVE Online Demo") {
    if (urlParams.get('sys') === "baseline") {
        config["route"] = "/best/"
    }
    engine = new Engine(config);
}
else if (title === "Baseline") {
    engine = new Baseline(config);
}

recognition.onresult = engine.sendHandler;
$("#btn-submit").click(engine.sendHandler);

const mode = urlParams.get('mode');
if (mode === "test") {
    $("#btn-dev").html("Go back");
    $("#btn-dev").click(() => {
        window.history.back();
    });
}
else if (mode === "study") {
    const studyEngine = new Study(urlParams, engine, config);
    // Submit query results to the server
    $("#submit_results").click(() => {
        const results = $("#result-text").val();
        studyEngine.send(results);
    });
}
else if (mode === "cognition") {
    const cognitionEngine = new Cognition(urlParams, engine, config);
}
else if (mode === "study2") {
    const studyEngine = new AmbiguousStudy(urlParams, engine, config);
}
else if (mode === "present") {
    const sender = urlParams.get('sender');
    const dataset = urlParams.get('dataset');
    const queryID = parseInt(urlParams.get('query'));

    const queries = {
        "large": [
            "SELECT count(*) FROM delayed_flight WHERE \"departure_city\" = 'Newark';",
            "SELECT count(*) FROM delayed_flight WHERE \"arrival_city\" = 'Newburgh';",
            "SELECT count(*) FROM delayed_flight WHERE \"departure_city\" = 'Chicago';",
            "SELECT count(*) FROM delayed_flight WHERE \"arrival_city\" = 'Washington';",
            "SELECT count(*) FROM delayed_flight WHERE \"departure_state\" = 'Florida';"
        ],
        "small": [
            "SELECT count(*) FROM sample_311 WHERE \"borough\" = 'BRONX';",
            "SELECT count(*) FROM sample_311 WHERE \"landmark\" = 'OCEAN AVENUE';",
            "SELECT count(*) FROM sample_311 WHERE \"descriptor type\" = 'Loud Talking';",
            "SELECT count(*) FROM sample_311 WHERE \"park borough\" = 'QUEENS';",
            "SELECT count(*) FROM sample_311 WHERE \"cross street 2\" = 'MANHATTAN';"
        ]
    };

    const text = {
        "large": [
            "Find departure city=Newark;",
            "Find arrival city=Newburgh;",
            "Find departure city=Chicago;",
            "Find arrival city=Washington;",
            "Find departure state=Florida;"
        ],
        "small": [
            "Find borough=BRONX;",
            "Find landmark=OCEAN AVENUE;",
            "Find descriptor type=Loud Talking;",
            "Find park borough=QUEENS;",
            "Find cross street 2=MANHATTAN AVENUE;"
        ]
    };

    const query = queries[dataset][queryID];
    const explanation = text[dataset][queryID];


    $("#present").prop('disabled', 'disabled');

    const name = dataset === "large" ? "delayed_flight" : "sample_311";
    const presenter = sender;
    engine.connector.sender.setCallback(engine.sender[presenter].callback);
    $("#spoken-text").val(explanation);
    const params = [name, query, $("#viz").width(), $("#planner").val(), Date.now(), presenter];
    engine.connector.send(params.join("|"));
}
else {
    // Empty string
    // Show or hide development message
    $("#btn-dev").click(() => {
        $('.longer.modal').modal('show');
    });

    $("#close_ok").click(() => {
        $('.longer.modal').modal('hide');
    });
}

