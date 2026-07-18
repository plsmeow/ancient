const canvas = document.getElementById('starCanvas');
const ctx = canvas.getContext('2d');

function resizeCanvas() {
    const dpr = window.devicePixelRatio || 1;
    canvas.width = window.innerWidth * dpr;
    canvas.height = window.innerHeight * dpr;
    canvas.style.width = window.innerWidth + 'px';
    canvas.style.height = window.innerHeight + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
}

resizeCanvas();
window.addEventListener('resize', resizeCanvas);

const stars = [];
const numStars = 60;

class Star {
    constructor() {
        this.reset();
        this.x = Math.random() * canvas.width;
        this.y = Math.random() * canvas.height;
    }

    reset() {
        if (Math.random() > 0.5) {
            this.x = Math.random() * (canvas.width * 1.5); 
            this.y = -Math.random() * 200 - 50;
        } else {
            this.x = canvas.width + Math.random() * 200 + 50;
            this.y = -Math.random() * 200 + Math.random() * canvas.height;
        }
        
        this.size = Math.random() * 1.5 + 0.5;
        this.speed = Math.random() * 8 + 4;
        this.length = Math.random() * 120 + 40;
        this.opacity = Math.random() * 0.8 + 0.2;
        this.hue = Math.random() * 40 + 200;
        this.angle = Math.PI / 4;
    }

    update() {
        this.x -= this.speed * Math.cos(this.angle);
        this.y += this.speed * Math.sin(this.angle);

        if (this.x < -this.length || this.y > canvas.height + this.length) {
            this.reset();
        }
    }

    draw() {
        ctx.save();
        ctx.globalAlpha = this.opacity;
        
        ctx.shadowBlur = this.size * 8; 
        ctx.shadowColor = `hsl(${this.hue}, 100%, 80%)`; 
        
        const tailX = this.x + this.length * Math.cos(this.angle);
        const tailY = this.y - this.length * Math.sin(this.angle);

        const gradient = ctx.createLinearGradient(this.x, this.y, tailX, tailY);
        gradient.addColorStop(0, '#ffffff');
        gradient.addColorStop(1, 'rgba(255, 255, 255, 0)');

        ctx.beginPath();
        ctx.strokeStyle = gradient;
        ctx.lineWidth = this.size;
        ctx.lineCap = 'round';
        ctx.moveTo(this.x, this.y);
        ctx.lineTo(tailX, tailY);
        ctx.stroke();

        ctx.restore();
    }
}

for (let i = 0; i < numStars; i++) {
    stars.push(new Star());
}

function animate() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    for (const star of stars) {
        star.update();
        star.draw();
    }

    requestAnimationFrame(animate);
}

window.onload = function() {
    animate();
};