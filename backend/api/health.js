export default function handler(_request, response) {
  response.status(200).json({ service: "VIPLA/BATO provider-neutral bridge", status: "ready", secret_in_client: false });
}
