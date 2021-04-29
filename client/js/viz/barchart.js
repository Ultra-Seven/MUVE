import _ from "underscore";
class Barchart {
    constructor(container, engine) {
        this.container = container;
        this.engine = engine;
        this.colorScales = [
            "#ff0000", "#fff200",
            "#00ff00", "#008255", "#5180dd",
            "#0055ff", "#2600ff"
        ];
    }
    drawBarChart(data, groupby) {
        const nrGroups = this.colorScales.length;
        this.data = data;
        const key = Object.keys(data[0]["results"])[0];
        const type = data[0]["type"];
        const context = data[0]["context"];
        let render_data = _.map(this.data, obj => {
            const element = obj["results"][key][0];
            const rank = Math.min(obj["rank"], nrGroups - 1);
            const color = obj["simColor"] || this.colorScales[rank];
            return {
                simRank: obj["simRank"],
                y: type === "agg" ? parseInt(element) : obj["results"][key].length,
                label: obj["label"],
                color: color,
                click: (e) => {
                    this.engine.end = Date.now();
                    alert("Timer stops! Please submit your results");
                },
                mouseover: (e) => {
                    console.log(this.engine.renderTime);
                    if (this.engine.renderTime === 0) {
                        this.engine.renderTime = Date.now();
                    }
                }
            }
        });
        render_data = _.sortBy(render_data, point => {
            return point["simRank"] || point["label"];
        });
        const title = context === "column" ?  "\"" + groupby + "\" = ?" :
            "? = \"" + groupby + "\"";
        const fontColor = context === "value" ? "#8803a9" : "#774a00";

        $("#" + this.container).CanvasJSChart({
            animationEnabled: true,
            title: {
                text: title,
                fontFamily: "Calibri, Optima, Candara, Verdana, Geneva, sans-serif",
                fontColor: fontColor
            },
            data: [{
                type: "column",
                indexLabel: "{y}",
                indexLabelPlacement: "outside",
                indexLabelOrientation: "horizontal",
                dataPoints: render_data
            }],
            axisX: {
                labelMaxWidth: 100,
                labelAngle: 90,
                interval: 1
            },
        });
    }

    drawBarChartUsingChartJS(data) {
        const ctx = document.getElementById("canvas_" + this.container).getContext('2d');
        this.data = data;
        const groupby = data[0]["groupby"];
        const type = data[0]["type"];
        const context = data[0]["context"];
        let render_data = _.map(this.data, obj => {
            const value = obj["results"];
            const highlighted = obj["highlighted"];
            return {
                y: type === "agg" ? parseInt(value) : obj["results"][key].length,
                label: obj["label"],
                color: highlighted ? this.colorScales[0] : "#0000ff"
            }
        });
        render_data = _.sortBy(render_data, point => {
            return point["label"];
        });
        const chart_data = {
            labels: _.map(render_data, dataPoint => dataPoint["label"]),
            datasets: [
                {
                    data: _.map(render_data, dataPoint => dataPoint["y"]),
                    backgroundColor: _.map(render_data, dataPoint => {
                        const rank = dataPoint["rank"];
                        const color = rank === 0 ? this.colorScales[0] : "#0000ff";
                        return this.hexToRgbA(color, 1);
                    }),
                    rank: _.map(render_data, dataPoint => dataPoint["rank"])
                },
            ]
        };
        const that = this;
        const title = context === "column" ?  "\"" + groupby + "\" = ?" :
            "? = \"" + groupby + "\"";
        const fontColor = context === "value" ? "#8803a9" : "#774a00";
        const options = {
            maintainAspectRatio: false,
            scales: {
                yAxes: [{
                    display: true,
                    stacked: true
                }],
                xAxes: [{
                    ticks: {
                        fontSize: 10,
                        autoSkip: false,
                        maxRotation: 90,
                        minRotation: 90
                    }
                }]
            },
            title: {
                display: true,
                fontSize: 20,
                fontColor: fontColor,
                text: title
            },
            legend: {
                display: false,
            },
            onClick: function(evt) {
                if (that.cognitionEnd === 0) {
                    const activePoints = this.getElementsAtEvent(evt);
                    const chartData = activePoints[0]['_chart'].config.data;
                    const idx = activePoints[0]['_index'];

                    const value = chartData.datasets[0].data[idx];
                    that.isMatch = that.targetValue === value ? 1 : 0;
                    that.cognitionEnd = Date.now();
                    console.log("Matched: " + that.isMatch + ", Time: " + (that.cognitionEnd - that.cognitionStart));
                    that.user = prompt("Timer stops! Please enter your worker id:", "worker");
                    that.send();
                }
            },
            // tooltips: {
            //     callbacks: {
            //         label: (tooltipItem) => {
            //             const label = tooltipItem["label"];
            //             const title = plot["title"];
            //             return title.replace("?", label);
            //         },
            //         title: () => {}
            //     },
            //     displayColors: false
            // }
        };


        this.chart = new Chart(ctx, {
            type: 'bar',
            data: chart_data,
            options: options
        });
    }

    drawPlotChartJS(data) {
        const ctx = document.getElementById("canvas_" + this.container).getContext('2d');
        this.data = data;
        const context = data[0]["context"];
        const groupby = data[0]["groupby"];
        let render_data = _.map(this.data, obj => {
            const element = obj["results"];
            const highlighted = obj["highlighted"];
            return {
                y: parseInt(element),
                label: obj["label"],
                color: highlighted ? this.colorScales[0] : "#0000ff"
            }
        });
        render_data = _.sortBy(render_data, point => {
            return point["label"];
        });
        const chart_data = {
            labels: _.map(render_data, dataPoint => dataPoint["label"]),
            datasets: [
                {
                    data: _.map(render_data, dataPoint => dataPoint["y"]),
                    backgroundColor: _.map(render_data, dataPoint => {
                        const color = dataPoint["color"];
                        return this.hexToRgbA(color, 1);
                    })
                },
            ]
        };
        const that = this;
        const title = context === "column" ?  "\"" + groupby + "\" = ?" :
            "? = \"" + groupby + "\"";
        const fontColor = context === "value" ? "#8803a9" : "#774a00";
        const options = {
            maintainAspectRatio: false,
            scales: {
                yAxes: [{
                    display: true,
                    stacked: true
                }],
                xAxes: [{
                    ticks: {
                        fontSize: 10,
                        autoSkip: false,
                        maxRotation: 90,
                        minRotation: 90
                    }
                }]
            },
            title: {
                display: true,
                fontSize: 20,
                fontColor: fontColor,
                text: title
            },
            legend: {
                display: false,
            },
            onClick: function(evt) {
                if (that.cognitionEnd === 0) {
                    const activePoints = this.getElementsAtEvent(evt);
                    const chartData = activePoints[0]['_chart'].config.data;
                    const idx = activePoints[0]['_index'];

                    const value = chartData.datasets[0].data[idx];
                    that.isMatch = that.targetValue === value ? 1 : 0;
                    that.cognitionEnd = Date.now();
                    console.log("Matched: " + that.isMatch + ", Time: " + (that.cognitionEnd - that.cognitionStart));
                    that.user = prompt("Timer stops! Please enter your worker id:", "worker");
                    that.send();
                }
            },
            // tooltips: {
            //     callbacks: {
            //         label: (tooltipItem) => {
            //             const label = tooltipItem["label"];
            //             const title = plot["title"];
            //             return title.replace("?", label);
            //         },
            //         title: () => {}
            //     },
            //     displayColors: false
            // }
        };


        this.chart = new Chart(ctx, {
            type: 'bar',
            data: chart_data,
            options: options
        });
    }

    updatePlotChartJS(data) {
        this.data = data;
        let render_data = _.map(this.data, obj => {
            const element = obj["results"];
            const highlighted = obj["highlighted"];
            return {
                y: parseInt(element),
                label: obj["label"],
                color: highlighted ? this.colorScales[0] : "#0000ff"
            }
        });
        render_data = _.sortBy(render_data, point => {
            return point["label"];
        });

        this.chart.data.datasets[0] = {
            data: _.map(render_data, dataPoint => dataPoint["y"]),
            backgroundColor: _.map(render_data, dataPoint => {
                const color = dataPoint["color"];
                return this.hexToRgbA(color, 1);
            })
        };
        this.chart.update(0);
    }

    hexToRgbA(hex, alpha){
        let c;
        if(/^#([A-Fa-f0-9]{3}){1,2}$/.test(hex)){
            c= hex.substring(1).split('');
            if(c.length === 3){
                c= [c[0], c[0], c[1], c[1], c[2], c[2]];
            }
            c = '0x'+c.join('');
            return 'rgba('+[(c>>16)&255, (c>>8)&255, c&255].join(',') + ',' + alpha + ')';
        }
        throw new Error('Bad Hex');
    }
}
export default Barchart;