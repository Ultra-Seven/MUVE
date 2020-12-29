import Barchart from "./viz/barchart";
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const recognition = new SpeechRecognition();
import _ from "underscore";
import Sample_311 from "./dataset/sample_311";
import Sample_AU from "./dataset/sample_au";

recognition.lang = 'en-US';
let name = $('select').val();
$("#btn-start-recording").click(() => {
    recognition.start();
    console.log('Ready to receive voice input.');
});

$('select').on('change', function() {
    name = this.value;
    if (name === "sample_311") {
        sample311.setup();
    }
    else if (name === "sample_au") {
        sampleAU.setup();
    }
});
let window_height = window.innerHeight
    || document.documentElement.clientHeight
    || document.body.clientHeight;
$("#list_content").height(Math.floor(window_height * 0.6) + "px");

$("#query_content").height(Math.floor(window_height * 0.12) + "px");

const sample311 = new Sample_311();
const sampleAU = new Sample_AU();

sample311.setup();

window.onresize = () => {
    window_height = window.innerHeight
        || document.documentElement.clientHeight
        || document.body.clientHeight;
    $("#list_content").height(Math.floor(window_height * 0.6) + "px");

    $("#query_content").height(Math.floor(window_height * 0.12) + "px");
}

let render_data;
const AJAX = false;
let ws;
if (!AJAX) {
    ws = new WebSocket("wss://localhost:7000/lucene/");
    ws.onmessage = msg => {
        console.log("Receiving data");
        const data = JSON.parse(msg.data);
        // $("#answer").html(data);
        render_data = data["data"];
        const template = data["debug"]
        if (render_data.length === 0) {
            $("#viz").empty();
            $("#viz_title").html("No Results for " + JSON.stringify(template));
        }
        else {
            draw(render_data)
        }
    };
}


function draw(query_results) {
    $("#viz").empty();
    $("#legend").empty();
    const charts = [];
    _.each(query_results, (row, idx) => {
        const groupBys = Object.keys(row);
        const nrFigures = groupBys.length;
        const rowName = "row_" + idx;
        $("#viz").append("<div id='" + rowName + "'></div>");
        for (let figureCtr = 0; figureCtr < nrFigures; figureCtr++) {
            const barName = "bar_" + idx + "_" + figureCtr;
            const group = groupBys[figureCtr];
            const width = row[group]["width"];
            const data = row[group]["data"];
            $("#" + rowName).append(
                "<div id='" + barName +
                "' style='width: " + width + "%; height: 280px;display: inline-block;'></div>"
            );
            const barChart = new Barchart(barName, []);
            charts.push(barChart);
            barChart.drawBarChart(data, group);
            // Title name
            const key = Object.keys(data[0]["results"])[0];
            const key_arr = _.filter(key.split(/[()]+/), element => element !== "");
            const aggTitle = key_arr[0];
            const target = (key_arr.length === 1 ?
                key_arr[0] : key_arr[1]).replaceAll("_"," ");
            let aggPrefix;
            if (aggTitle.startsWith("max")) {
                aggPrefix = "Maximum of ";
            }
            else if (aggTitle.startsWith("max")) {
                aggPrefix = "Minimum of ";
            }
            else {
                aggPrefix = "Count of ";
            }
            $("#viz_title").html("Visualizations for " + aggPrefix + target);
        }

    });
    const scales = charts[0].colorScales;
    _.each(scales, (color, idx) => {
        const id = "color_" + idx;
        $("#legend").append("<button class='ui button' id='" + id +"'></button>");
        $("#" + id).css("background-color", color);
        $("#" + id).width("80px");
        $("#" + id).height("30px");
    });
    $("#color_0").html("High").css('color', 'white').css("fontSize", 20);
    $("#color_" + (scales.length - 1)).html("Low").css('color', 'white').css("fontSize", 20);
    // Remove watermarks
    $('.canvasjs-chart-credit').remove();
}
recognition.onresult = function(event) {
    const sentences = event.results[0][0].transcript;
    $("#spoken-text").val(sentences);
    console.log(sentences);
    if (name) {
        // ws.send(name + ";" + sentences);
        if (AJAX) {
            $.ajax({
                type: "POST",
                crossDomain: true,
                url: "https://localhost:7000",
                data: name + ";" + sentences,
                dataType: "text",
                success: resultData => {
                    console.log("Receiving data");
                    render_data = JSON.parse(resultData)
                    // $("#answer").html(data);
                    draw(render_data)
                }
            });
        }
        else {
            ws.send(name + ";" + sentences + ";" + $("#viz").width());
        }
    }
}

$("#btn-submit").click(() => {
    const sentences = $("#spoken-text").val();
    console.log(sentences);
    if (name) {
        // ws.send(name + ";" + sentences);
        if (AJAX) {
            $.ajax({
                type: "POST",
                crossDomain: true,
                url: "https://localhost:7000",
                data: name + ";" + sentences,
                dataType: "text",
                success: resultData => {
                    console.log("Receiving data");
                    render_data = JSON.parse(resultData)
                    // $("#answer").html(data);
                    draw(render_data)
                }
            });
        }
        else {
            ws.send(name + ";" + sentences + ";" + $("#viz").width());
        }
    }
});

$("#btn-dev").click(() => {
    $('#detailed_response').html(JSON.stringify(render_data));
    $('.longer.modal').modal('show');
});

$("#close_ok").click(() => {
    $('.longer.modal').modal('hide');
});

