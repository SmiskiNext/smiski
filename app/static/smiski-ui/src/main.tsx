/**
 * Custom UI entry point.
 *
 * Mounts the single <App /> that both Forge modules share. App inspects the
 * Forge module context to decide which root surface to render.
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import { App } from './App';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>,
);
