from flask import Flask, request, jsonify, send_file
#from flask_sslify import SSLify
import json
from tempfile import NamedTemporaryFile
import os
from funasr import AutoModel
import logging
from logging.handlers import RotatingFileHandler

model_online=True # or False if you have downloaded the model and want to load it from local path, otherwise it will be loaded from online by default

# Configure logging
app = Flask(__name__)

log_handler = RotatingFileHandler(os.path.join(os.getcwd(), "audio.log"), maxBytes=1024*1024*10, backupCount=5, encoding='utf-8')
log_handler.setLevel(logging.DEBUG)
formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
log_handler.setFormatter(formatter)

# Attach handler to root logger
logging.getLogger().addHandler(log_handler)
logging.getLogger().setLevel(logging.DEBUG)

# Attach handler to app.logger
app.logger.addHandler(log_handler)
app.logger.setLevel(logging.DEBUG)

# Test log entry
logging.info("Root logger initialized.")
app.logger.info("App logger initialized.")

# use vad, punc, spk or not as you need
if model_online:
    model = AutoModel(model="paraformer-zh",  
                    vad_model="fsmn-vad",  
                    punc_model="ct-punc",
                    spk_model="cam++",
                    device="cuda"
                    )
else:
    model = r"C:\Users\youdiyu\.cache\modelscope\hub\models\iic\speech_seaco_paraformer_large_asr_nat-zh-cn-16k-common-vocab8404-pytorch"
    vad_model = r"C:\Users\youdiyu\.cache\modelscope\hub\models\iic\speech_fsmn_vad_zh-cn-16k-common-pytorch"
    punc_model = r"C:\Users\youdiyu\.cache\modelscope\hub\models\iic\punc_ct-transformer_cn-en-common-vocab471067-large"
    spk_model = r"C:\Users\youdiyu\.cache\modelscope\hub\models\iic\speech_campplus_sv_zh-cn_16k-common"

    model = AutoModel(model=model,  
                    vad_model=vad_model,  
                    punc_model=punc_model,
                    spk_model=spk_model,
                    device="cuda"
                    )

# Initialize SSLify to enforce HTTPS
# nginx + waitress combination is recommended for production deployment, and SSL termination can be handled by nginx, so SSLify is not necessary in this case. If you want to run the Flask app with SSL directly, you can uncomment the following line and provide the paths to your certificate and key files.
# nginx + waitress 组合是推荐的生产部署方案，SSL 终止可以由 nginx 处理，所以在这种情况下不需要 SSLify。如果你想直接使用 SSL 运行 Flask 应用，可以取消注释以下行，并提供你的证书和密钥文件的路径。
# sslify = SSLify(app)

def validate_bearer_token(auth_header):
    if not auth_header or not auth_header.startswith("Bearer "):
        return False
    token = auth_header.split(" ")[1]
    # Replace with actual token validation logic
    return True

@app.route("/", methods=["GET"])
def home():
    image_path = "./nailao_02.jpg"  # 例如: "static/images/welcome.jpg"
    return send_file(image_path, mimetype='image/jpeg')

@app.route("/speech_to_text", methods=["POST"])
def speech_to_text():
    duration_seconds = 0
    duration_ms = 0
    auth_header = request.headers.get("Authorization")
    if not validate_bearer_token(auth_header):
        app.logger.warning("Unauthorized access attempt to speech_to_text endpoint.")
        return jsonify({"error": "Unauthorized"}), 401

    if "audio_file" not in request.files:
        app.logger.error("No audio file provided in the request.")
        return jsonify({"success": False,
                        "error": "No audio file provided",
                        "text": "识别的文字内容"}), 400

    audio_file = request.files["audio_file"]
    uid = request.form.get("uid", "unknown")
    app.logger.info(f"Processing audio file for user: {uid}")

    # Save the uploaded file temporarily
    with NamedTemporaryFile(delete=False, suffix=".wav") as tmp:
        audio_file.save(tmp.name)
        tmp_path = tmp.name

    try:
        res = model.generate(input=tmp_path,
                         batch_size_s=300,
                         #use_itn=True,
                         return_spk_res=True, 
                         merge_vad_segments=True, 
                         punctuate=True,
                         #hotword='魔搭'
                         )
        if res:
            app.logger.info(f"Audio processing successful. {audio_file.name}")
            app.logger.info(f"{res}")
            sentences = []
            for sentence in res[0].get("sentence_info", []):
                sentences.append({"text":f'spk_{sentence.get("spk",0)}: {sentence.get("text", "")}', "start":sentence["start"], "end":sentence["end"]})
            return app.response_class(
                response=json.dumps({
                    "success": True,
                    "text": sentences,
                    "duration": duration_seconds
                }, ensure_ascii=False),
                mimetype='application/json'
            )
        else:
            app.logger.error("Audio processing failed: No result returned.")
            return app.response_class(
                response=json.dumps({
                    "success": False,
                    "error": "No audio file provided",
                    "text": "",
                    "duration": duration_seconds
                }, ensure_ascii=False),
                mimetype='application/json'
            )
    finally:
        # Clean up temporary file
        os.remove(tmp_path)
        app.logger.info("Temporary file cleaned up.")

if __name__ == "__main__":
    # app.run(host='::', port=9204, ssl_context=(r'C:\Users\You\cert.pem', r'C:\Users\You\key.pem'))
    app.run(port=9204)#, ssl_context=(r'C:\Users\You\cert.pem', r'C:\Users\You\key.pem'))
