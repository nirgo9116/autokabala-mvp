import express from "express";
import dotenv from "dotenv";
import Anthropic from "@anthropic-ai/sdk";

dotenv.config();

const app = express();
app.use(express.json());

const anthropic = new Anthropic({
  apiKey: process.env.ANTHROPIC_API_KEY
});

app.get("/", (req, res) => {
  res.send("AutoKabala Backend Running");
});

app.post("/extract-payment", async (req, res) => {

  const text = req.body.text;

  try {

    const message = await anthropic.messages.create({
      model: "claude-3-5-sonnet",
      max_tokens: 200,
      messages: [
        {
          role: "user",
          content: `Extract name, amount and date from this payment text: ${text}`
        }
      ]
    });

    res.json(message.content);

  } catch (error) {
    res.status(500).send(error.message);
  }

});

app.listen(3000, () => {
  console.log("Server running on port 3000");
});