(()=>{
  'use strict';

  document.querySelectorAll('.confirm-delete').forEach(form=>form.addEventListener('submit',event=>{
    if(!confirm('Delete this item permanently?')) event.preventDefault();
  }));
  document.querySelectorAll('.delete-step-button').forEach(button=>button.addEventListener('click',event=>{
    if(!confirm('Delete this step permanently?')) event.preventDefault();
  }));

  const previewFile=(input,preview)=>{
    const file=input.files[0];
    if(!file||!preview) return;
    const previous=preview.dataset.objectUrl;
    if(previous) URL.revokeObjectURL(previous);
    const url=URL.createObjectURL(file);
    preview.dataset.objectUrl=url;
    preview.src=url;
    preview.classList.remove('d-none');
  };

  document.querySelectorAll('.media-input').forEach(input=>input.addEventListener('change',()=>{
    const file=input.files[0];
    const form=input.closest('form');
    const image=form?.querySelector('.selected-media-preview');
    const video=form?.querySelector('.video-preview');
    if(!file||!form||!image||!video) return;
    if(file.type.startsWith('video/')){
      previewFile(input,video);
      image.classList.add('d-none');
    }else{
      previewFile(input,image);
      video.classList.add('d-none');
    }
  }));
  document.querySelectorAll('.thumbnail-input').forEach(input=>input.addEventListener('change',()=>{
    previewFile(input,input.closest('form')?.querySelector('.thumbnail-preview'));
  }));

  document.querySelectorAll('.xhr-form').forEach(form=>form.addEventListener('submit',event=>{
    event.preventDefault();
    const xhr=new XMLHttpRequest();
    const progress=form.querySelector('.progress');
    const bar=form.querySelector('.progress-bar');
    const button=form.querySelector('button[type="submit"],button.btn-primary');
    if(button) button.disabled=true;
    if(progress) progress.classList.remove('d-none');
    xhr.upload.addEventListener('progress',uploadEvent=>{
      if(uploadEvent.lengthComputable&&bar){
        const percent=Math.round(uploadEvent.loaded/uploadEvent.total*100);
        bar.style.width=percent+'%';
        bar.textContent=percent+'%';
      }
    });
    xhr.addEventListener('load',()=>{
      if(xhr.status>=200&&xhr.status<400){
        location.href=xhr.responseURL;
      }else{
        document.open();
        document.write(xhr.responseText);
        document.close();
      }
    });
    xhr.addEventListener('error',()=>{
      if(button) button.disabled=false;
      alert('Upload failed. Check your connection and retry.');
    });
    xhr.open('POST',form.action||location.href);
    xhr.send(new FormData(form));
  }));

  const list=document.querySelector('#sortable-steps');
  if(!list) return;
  let dragged=null;
  list.querySelectorAll('[draggable]').forEach(item=>{
    item.addEventListener('dragstart',()=>{dragged=item;item.classList.add('dragging');});
    item.addEventListener('dragend',()=>{item.classList.remove('dragging');dragged=null;});
    item.addEventListener('dragover',event=>{
      event.preventDefault();
      if(dragged&&dragged!==item){
        const box=item.getBoundingClientRect();
        list.insertBefore(dragged,event.clientY<box.top+box.height/2?item:item.nextSibling);
      }
    });
  });
  let touched=null;
  list.querySelectorAll('.drag-handle').forEach(handle=>{
    handle.addEventListener('touchstart',()=>{touched=handle.closest('.list-group-item');},{passive:true});
    handle.addEventListener('touchmove',event=>{
      if(!touched) return;
      const point=event.touches[0];
      const under=document.elementFromPoint(point.clientX,point.clientY)?.closest('.list-group-item');
      if(under&&under!==touched){
        list.insertBefore(touched,point.clientY<under.getBoundingClientRect().top+under.offsetHeight/2?under:under.nextSibling);
      }
      event.preventDefault();
    },{passive:false});
    handle.addEventListener('touchend',()=>{touched=null;});
  });
})();
