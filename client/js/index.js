import Baseline from "./engine/baseline";

const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const recognition = new SpeechRecognition();
import Sample_311 from "./dataset/sample_311";
import Sample_AU from "./dataset/sample_au";
import Config from "./config";
import Engine from "./engine/engine";
import Study from "./exp/user_study";
import Dob_Job from "./dataset/dob_job";
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
if (title === "Optimal Interfaces") {
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

