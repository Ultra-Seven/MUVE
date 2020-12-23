import Barchart from "./viz/barchart";
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const recognition = new SpeechRecognition();
import _ from "underscore";

recognition.lang = 'en-US';
let name = $('select').val();
$("#btn-start-recording").click(() => {
    recognition.start();
    console.log('Ready to receive voice input.');
});
$('select').on('change', function() {
    name = this.value;
    console.log(name)
});


const ws = new WebSocket("wss://localhost:7000/lucene/");
let render_data;

ws.onmessage = msg => {
    console.log("Receiving data");
    const data = msg.data;
    render_data = JSON.parse(data)
    // $("#answer").html(data);
    draw(render_data)
};

function draw(query_results) {
    $("#viz").empty();
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
                "' style='width: " + width + "px; height: 280px;display: inline-block;'></div>"
            );
            const barChart = new Barchart(barName, []);
            barChart.drawBarChart(data, group);
        }

    });
    // Remove watermarks
    $('.canvasjs-chart-credit').remove();
}
recognition.onresult = function(event) {
    const sentences = event.results[0][0].transcript;
    $("#spoken-text").html(sentences);
    console.log(sentences);
    if (name) {
        ws.send(name + ";" + sentences);
    }
}

