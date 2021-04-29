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

sample311.setup();

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
if (title === "MUVE Online Demo") {
    engine = new Engine(config);
}
else if (title === "Baseline") {
    engine = new Baseline(config);
}

recognition.onresult = engine.sendHandler;
$("#btn-submit").click(engine.sendHandler);

const queryString = window.location.search;
const urlParams = new URLSearchParams(queryString);
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
else if (mode === "present") {
    const query = "SELECT count(*) FROM delayed_flight WHERE \"city\" = 'Newark';";
    const sender = urlParams.get('sender');
    const element = $('#present').children().eq(sender);
    $(element).prop('selected', true)
        .trigger('change');

    $("#present").prop('disabled', 'disabled');

    const sentences = $("#spoken-text").val();
    const name = "delayed_flight";
    const presenter = sender;
    engine.connector.sender.setCallback(engine.sender[presenter].callback);
    $("#spoken-text").val("Find arrival city=Newark");
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

