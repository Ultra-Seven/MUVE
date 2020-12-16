// import RecordRTC from 'recordrtc';
// import Barchart from "../viz/barchart";
// const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
// class Record {
//     constructor() {
//         this.paragraph = document.createElement('p');
//         this.isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);
//         this.isEdge = navigator.userAgent.indexOf('Edge') !== -1;
//         this.btnStartRecording = $("#btn-start-recording");
//         this.btnStopRecording = $("#btn-stop-recording");
//         this.btnReleaseMicrophone = $("#btn-release-microphone");
//         this.btnDownloadRecording = $("#btn-download-recording");
//         this.btnMicrophone = $("#microphone-icon");
//         this.audio = document.querySelector('audio');
//         this.recognition = new SpeechRecognition();
//         this.recognition.interimResults = true;
//         this.recognition.lang = 'en-US';
//         this.barChart = new Barchart("bar", []);
//         const container = document.querySelector('.text-box');
//         container.appendChild(this.paragraph);
//         this.btnStartRecording.click(() => {
//             // Disable actions on the button
//             this.btnStartRecording.prop( "disabled", true );
//             this.btnStartRecording.css('border','');
//             this.btnStartRecording.css('fontSize','');
//             const setSrcObject = function (stream, element) {
//                 if ('srcObject' in element) {
//                     element.srcObject = stream;
//                 } else if ('mozSrcObject' in element) {
//                     element.mozSrcObject = stream;
//                 } else {
//                     element.srcObject = stream;
//                 }
//             };
//             if (!this.microphone) {
//                 console.log("here");
//                 this.captureMicrophone((mic) => {
//                     this.microphone = mic;
//
//                     if(this.isSafari) {
//                         this.replaceAudio(null);
//
//                         this.audio.muted = true;
//                         setSrcObject(this.microphone, this.audio);
//                         this.audio.play();
//
//                         this.btnStartRecording.prop( "disabled", false );
//                         this.btnStartRecording.css("border", '1px solid red');
//                         this.btnStartRecording.css("fontSize", '150%');
//
//                         alert('Please click startRecording button again. First time we tried to access your microphone. Now we will record it.');
//                         return;
//                     }
//
//                     this.click(this.btnStartRecording);
//                 });
//                 return;
//             }
//             this.dictate();
//             this.replaceAudio(null);
//
//             this.audio.muted = true;
//             setSrcObject(this.microphone, this.audio);
//             this.audio.play();
//
//             let options = {
//                 type: 'audio',
//                 numberOfAudioChannels: this.isEdge ? 1 : 2,
//                 checkForInactiveTracks: true,
//                 bufferSize: 16384
//             };
//
//             if(navigator.platform && navigator.platform.toString().toLowerCase().indexOf('win') === -1) {
//                 options["sampleRate"] = 48000; // or 44100 or remove this line for default
//             }
//
//             if(this.recorder) {
//                 this.recorder.destroy();
//                 this.recorder = null;
//             }
//
//             this.recorder = RecordRTC(this.microphone, options);
//
//             this.recorder.startRecording();
//
//             this.btnStopRecording.prop("disabled", false);
//             this.btnDownloadRecording.prop("disabled", true);
//         });
//
//         this.btnStopRecording.click(() => {
//             this.btnStopRecording.prop("disabled", true);
//             const stopRecordingCallback = () => {
//                 this.replaceAudio(URL.createObjectURL(this.recorder.getBlob()));
//
//                 this.btnStartRecording.prop("disabled", false);
//
//                 setTimeout(() => {
//                     if(!this.audio.paused) return;
//
//                     setTimeout(() => {
//                         if(!this.audio.paused) return;
//                         this.audio.play();
//                     }, 1000);
//
//                     this.audio.play();
//                 }, 300);
//
//                 this.audio.play();
//
//                 this.btnDownloadRecording.prop( "disabled", false);
//                 if(this.isSafari) {
//                     this.click(this.btnReleaseMicrophone);
//                 }
//             };
//             this.recorder.stopRecording(stopRecordingCallback);
//         });
//
//         this.btnReleaseMicrophone.click(() => {
//             this.btnReleaseMicrophone.prop( "disabled", true );
//             this.btnStartRecording.prop( "disabled", false );
//
//             if(this.microphone) {
//                 this.microphone.stop();
//                 this.microphone = null;
//             }
//
//             if(this.recorder) {
//                 // click(btnStopRecording);
//             }
//         });
//
//         this.btnDownloadRecording.click(() => {
//             const getFileName = (extension) => {
//                 const d = new Date();
//                 const year = d.getFullYear();
//                 const month = d.getMonth();
//                 const date = d.getDate();
//
//                 const randomName = (Math.random() * new Date().getTime()).toString(36)
//                     .replace(/\./g, '');
//
//                 return 'RecordRTC-' + year + month + date + '-' + randomName + '.' + extension;
//             };
//             this.btnDownloadRecording.prop( "disabled", true );
//             if(!this.recorder || !this.recorder.getBlob()) return;
//
//             // if(this.isSafari) {
//             //     this.recorder.getDataURL((dataURL) => {
//             //         this.saveToDisk(dataURL, getFileName('mp3'));
//             //     });
//             //     return;
//             // }
//
//             const blob = this.recorder.getBlob();
//             const file = new File([blob], getFileName('mp3'), {
//                 type: 'audio/mp3'
//             });
//             console.log(file);
//
//             const getBase64 = (file) => {
//                 return new Promise((resolve, reject) => {
//                     const reader = new FileReader();
//                     reader.readAsDataURL(file);
//                     reader.onload = () => resolve(reader.result);
//                     reader.onerror = error => reject(error);
//                 });
//             };
//             // invokeSaveAsDialog(file);
//             let final_transcript = $('#spoken-text p').html();
//             // let final_transcript = "return me the homepage of PVLDB.";
//             console.log(final_transcript);
//             getBase64(file).then(
//                 audio_data => {
//                     $.ajax({
//                         type: "POST",
//                         contentType: "application/json; charset=utf-8",
//                         url: "/voice",
//                         data: JSON.stringify({
//                             user: person,
//                             voice_input: final_transcript,
//                             actual_input: final_transcript,
//                             voice_time: this.voice_time,
//                             audio: audio_data,
//                             latency: this.latency
//                         }),
//                         success: function(data) {
//                             console.log("Save user record into the database");
//                         },
//                         dataType: "json"
//                     });
//                 }
//             );
//         });
//
//         this.btnMicrophone.click(() => {
//             this.btnStartRecording.click();
//         });
//     }
//
//     dictate() {
//         this.recognition.start();
//         console.log("recognition start");
//         let one_time_sentence = '';
//         let start1 = Date.now();
//         this.recognition.onresult = (event) => {
//             let interim_transcript = '';
//             let final_transcript = '';
//             for (let i = event.resultIndex; i < event.results.length; ++i) {
//                 if (event.results[i].isFinal) {
//                     final_transcript += event.results[i][0].transcript;
//                     console.log("final_transcript", final_transcript);
//                     const inter_end = Date.now();
//                     this.paragraph.textContent = final_transcript;
//                     this.voice_time = inter_end - start1;
//                     $( "#btn-stop-recording" ).click();
//                     $.ajax({
//                         type: "POST",
//                         contentType: "application/json; charset=utf-8",
//                         url: "/dialogflow",
//                         data:  JSON.stringify({
//                             user: "person",
//                             query: final_transcript
//                         }),
//                         success: (data) => {
//                             console.log(data);
//                             const result_end = Date.now();
//                             // Render the barchart
//                             if (data.result.length > 0) {
//                                 this.barChart.drawBarChart(data.result);
//                             }
//                             else {
//                                 alert("No matched query!")
//                             }
//                             this.latency = result_end - inter_end;
//                             this.btnDownloadRecording.click();
//                         },
//                         dataType: "json"
//                     });
//                 } else {
//                     interim_transcript += event.results[i][0].transcript;
//                     if (i === event.results.length - 1 && one_time_sentence !== interim_transcript) {
//                         one_time_sentence = interim_transcript;
//                         console.log("interim_transcript", interim_transcript);
//                         // TODO:prediction
//
//                     }
//
//                 }
//             }
//         }
//     }
//
//     captureMicrophone(callback) {
//         this.btnReleaseMicrophone.prop( "disabled", false );
//         if(this.microphone) {
//             callback(this.microphone);
//             return;
//         }
//
//         if(typeof navigator.mediaDevices === 'undefined' || !navigator.mediaDevices.getUserMedia) {
//             alert('This browser does not supports WebRTC getUserMedia API.');
//
//             if(!!navigator.getUserMedia) {
//                 alert('This browser seems supporting deprecated getUserMedia API.');
//             }
//         }
//
//         navigator.mediaDevices.getUserMedia({
//             audio: this.isEdge ? true : {
//                 echoCancellation: false
//             }
//         }).then(function(mic) {
//             callback(mic);
//         }).catch(function(error) {
//             alert('Unable to capture your microphone. Please check console logs.');
//             console.error(error);
//         });
//     }
//
//     replaceAudio(src) {
//         const newAudio = document.createElement('audio');
//         newAudio.controls = true;
//
//         if(src) {
//             newAudio.src = src;
//         }
//
//         const parentNode = this.audio.parentNode;
//         parentNode.innerHTML = '';
//         parentNode.appendChild(newAudio);
//
//         this.audio = newAudio;
//     }
//
//     click(el) {
//         // Make sure that element is not disabled
//         el.prop( "disabled", false );
//         const evt = document.createEvent('Event');
//         evt.initEvent('click', true, true);
//         el.trigger("click");
//     }
//
//     // saveToDisk(fileURL, fileName) {
//     //     // for non-IE
//     //     if (!window.ActiveXObject) {
//     //         var save = document.createElement('a');
//     //         save.href = fileURL;
//     //         save.download = fileName || 'unknown';
//     //         save.style = 'display:none;opacity:0;color:transparent;';
//     //         (document.body || document.documentElement).appendChild(save);
//     //
//     //         if (typeof save.click === 'function') {
//     //             save.click();
//     //         } else {
//     //             save.target = '_blank';
//     //             var event = document.createEvent('Event');
//     //             event.initEvent('click', true, true);
//     //             save.dispatchEvent(event);
//     //         }
//     //
//     //         (window.URL || window.webkitURL).revokeObjectURL(save.href);
//     //     }
//     //
//     //     // for IE
//     //     else if (!!window.ActiveXObject && document.execCommand) {
//     //         var _window = window.open(fileURL, '_blank');
//     //         _window.document.close();
//     //         _window.document.execCommand('SaveAs', true, fileName || fileURL);
//     //         _window.close();
//     //     }
//     // }
// }
//
// export default Record;