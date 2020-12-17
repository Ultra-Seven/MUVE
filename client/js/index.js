import Barchart from "./viz/barchart";

const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const recognition = new SpeechRecognition();
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


const ws = new WebSocket("ws://localhost:80/lucene");
import _ from "underscore";
let render_data;

ws.onmessage = msg => {
    console.log("Receiving data");
    const data = msg.data;
    render_data = JSON.parse(data)
    // $("#answer").html(data);
    draw(render_data)
};

function draw(query_results) {
    const groupBys = Object.keys(query_results);
    const nrFigures = groupBys.length;
    $("#viz").empty();
    for (let figureCtr = 0; figureCtr < nrFigures; figureCtr++) {
        const barName = "bar_" + figureCtr
        const group = groupBys[figureCtr];
        $( "#viz" ).append( "<div id='" + barName + "' style='width: 85%; height: 280px;display: inline-block;'></div>");
        const barChart = new Barchart(barName, []);
        barChart.drawBarChart(query_results[group], group);
    }
    // Remove watermarks
    $('.canvasjs-chart-credit').remove();

    console.log($("#bar_0").CanvasJSChart());
}
recognition.onresult = function(event) {
    const sentences = event.results[0][0].transcript;
    $("#spoken-text").html(sentences);
    console.log(sentences);
    if (name) {
        ws.send(name + ";" + sentences);
    }
}

