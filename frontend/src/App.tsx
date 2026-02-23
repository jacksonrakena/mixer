import { CssBaseline, Container } from "@mui/material";
import { AssetChart } from "./AssetChart";
import "./App.css";

function App() {
  return (
    <>
      <CssBaseline />
      <Container maxWidth="lg" sx={{ padding: "20px 0" }}>
        <AssetChart />
      </Container>
    </>
  );
}

export default App;
